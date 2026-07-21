// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A readable GC log source. A source is either a file or one non-directory
 * entry in a ZIP file.
 */
public final class GCLogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_2 = 0x4b;
    /** Buffer size used when an archive does not publish its entry size. */
    private static final int SIZE_BUFFER_SIZE = 8192;

    /** Path to the physical source. */
    private final Path path;
    /** ZIP entry name, or {@code null} for a physical file. */
    private final String archiveEntry;
    /** Source storage format. */
    private final Format format;
    /** Published ZIP entry size, or {@code -1}. */
    private final long archiveEntrySize;

    private GCLogSource(final Path sourcePath, final String entryName,
                        final Format sourceFormat, final long entrySize) {
        this.path = Objects.requireNonNull(sourcePath);
        this.archiveEntry = entryName;
        this.format = Objects.requireNonNull(sourceFormat);
        this.archiveEntrySize = entrySize;
    }

    /**
     * Finds readable sources at a path. Directories yield their immediate
     * regular files and ZIP files yield their non-directory entries.
     *
     * @param sourcePath file or directory to inspect
     * @return the sources found at the path, in filesystem or archive order
     * @throws IOException if the path cannot be inspected
     */
    public static List<GCLogSource> discover(final Path sourcePath)
            throws IOException {
        Format sourceFormat = formatOf(sourcePath);
        if (sourceFormat == Format.DIRECTORY) {
            try (Stream<Path> paths = Files.list(sourcePath)) {
                return paths.filter(Files::isRegularFile)
                        .map(GCLogSource::uncheckedFileSource)
                        .collect(Collectors.toList());
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
        if (sourceFormat == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(sourcePath.toFile())) {
                return zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(entry -> zipSource(sourcePath, entry))
                        .collect(Collectors.toList());
            }
        }
        return List.of(new GCLogSource(sourcePath, null, sourceFormat, -1L));
    }

    /**
     * Returns the first readable source at a path.
     *
     * @param sourcePath file or directory to inspect
     * @return the first readable source
     * @throws IOException if the path cannot be inspected or contains no
     * readable source
     */
    public static GCLogSource first(final Path sourcePath) throws IOException {
        return discover(sourcePath).stream()
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "No log source found in " + sourcePath));
    }

    /**
     * Returns a specific non-directory entry in a ZIP file.
     *
     * @param sourcePath path to the ZIP file
     * @param entryName name of the entry
     * @return a source for the requested entry
     * @throws IOException if the entry does not exist or is a directory
     */
    public static GCLogSource zipEntry(final Path sourcePath,
                                       final String entryName)
            throws IOException {
        try (ZipFile zipFile = new ZipFile(sourcePath.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return zipSource(sourcePath, entry);
        }
    }

    /**
     * Detects a source format from its magic bytes rather than its file
     * extension.
     *
     * @param sourcePath path to inspect
     * @return detected format
     * @throws IOException if the path cannot be inspected
     */
    public static Format formatOf(final Path sourcePath) throws IOException {
        if (Files.isDirectory(sourcePath)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(sourcePath)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the underlying file path.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the archive entry name, or the file name for a non-archive
     * source.
     *
     * @return source name
     */
    public String name() {
        Path fileName = path.getFileName();
        return archiveEntry == null
                ? Objects.toString(fileName, path.toString()) : archiveEntry;
    }

    /**
     * Returns the source size in bytes. ZIP entries report their uncompressed
     * size; other sources report the size of the underlying file.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long sizeInBytes() throws IOException {
        if (archiveEntry == null) {
            return Files.size(path);
        }
        if (archiveEntrySize >= 0L) {
            return archiveEntrySize;
        }
        try (InputStream input = openStream()) {
            long size = 0L;
            byte[] buffer = new byte[SIZE_BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
            }
            return size;
        }
    }

    /**
     * Opens the source as bytes. Closing the returned stream also closes any
     * archive resources.
     *
     * @return source input stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        if (archiveEntry != null) {
            return openZipEntry();
        }
        InputStream input = new BufferedInputStream(Files.newInputStream(path));
        if (format != Format.GZIP) {
            return input;
        }
        try {
            return new GZIPInputStream(input);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    /**
     * Opens the source as UTF-8 lines. Closing the stream closes the underlying
     * file or archive.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    @SuppressWarnings("resource")
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                openStream(), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openZipEntry() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(archiveEntry);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + archiveEntry);
            }
            return new ZipEntryInputStream(zipFile.getInputStream(entry),
                    zipFile);
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private static GCLogSource uncheckedFileSource(final Path sourcePath) {
        try {
            return new GCLogSource(sourcePath, null, formatOf(sourcePath), -1L);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static GCLogSource zipSource(final Path sourcePath,
                                         final ZipEntry entry) {
        return new GCLogSource(sourcePath, entry.getName(), Format.ZIP,
                entry.getSize());
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Source storage format. */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed file. */
        GZIP,
        /** Filesystem directory. */
        DIRECTORY
    }

    private static final class ZipEntryInputStream extends FilterInputStream {
        /** Archive owning the wrapped entry stream. */
        private final ZipFile zipFile;

        private ZipEntryInputStream(final InputStream input,
                                    final ZipFile sourceZipFile) {
            super(input);
            this.zipFile = sourceZipFile;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                zipFile.close();
            }
        }
    }
}
