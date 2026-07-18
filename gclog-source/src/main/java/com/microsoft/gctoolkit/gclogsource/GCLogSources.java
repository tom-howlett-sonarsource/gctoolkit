// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * File-system and archive operations shared by GC log consumers.
 */
public final class GCLogSources {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_FIRST = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private GCLogSources() {
    }

    /**
     * Detects a source format from its type and magic bytes.
     *
     * @param path source path
     * @return detected source format
     */
    public static GCLogFileFormat formatOf(final Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return GCLogFileFormat.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return GCLogFileFormat.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return GCLogFileFormat.ZIP;
            }
        } catch (IOException ignored) {
            return GCLogFileFormat.PLAIN_TEXT;
        }
        return GCLogFileFormat.PLAIN_TEXT;
    }

    /**
     * Lists the direct children of a directory.
     *
     * @param directory directory to inspect
     * @return direct child paths
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> inDirectory(final Path directory)
            throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    /**
     * Lists sibling paths whose file names start with a prefix.
     *
     * @param path path whose parent should be searched
     * @param prefix required file-name prefix
     * @return matching sibling paths
     * @throws IOException if the parent directory cannot be listed
     */
    public static List<Path> siblingsStartingWith(final Path path,
                                                  final String prefix)
            throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }
        try (Stream<Path> paths = Files.list(parent)) {
            return paths.filter(candidate -> candidate.getFileName()
                            .toString().startsWith(prefix))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Lists non-directory entries in a ZIP archive.
     *
     * @param path ZIP archive path
     * @return entry names in archive order
     * @throws IOException if the archive cannot be read
     */
    public static List<String> zipEntries(final Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Returns the source size in bytes.
     *
     * @param path source path
     * @return source size
     * @throws IOException if the size cannot be read
     */
    public static long size(final Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Returns the uncompressed size of a ZIP entry.
     *
     * @param path ZIP archive path
     * @param zipEntryName entry name
     * @return uncompressed entry size
     * @throws IOException if the archive or entry cannot be read
     */
    public static long size(final Path path, final String zipEntryName)
            throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(zipEntryName);
            if (entry == null) {
                throw new IOException("ZIP entry not found: " + zipEntryName);
            }
            return entry.getSize();
        }
    }

    /**
     * Opens a plain file, GZIP file, or first non-directory ZIP entry.
     *
     * @param path source path
     * @return decompressed source stream
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(final Path path) throws IOException {
        GCLogFileFormat format = formatOf(path);
        if (format == GCLogFileFormat.PLAIN_TEXT) {
            return Files.newInputStream(path);
        }
        if (format == GCLogFileFormat.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == GCLogFileFormat.ZIP) {
            ZipInputStream input = new ZipInputStream(
                    Files.newInputStream(path));
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return input;
        }
        throw new IOException("Unable to read " + path);
    }

    /**
     * Opens a named ZIP entry.
     *
     * @param path ZIP archive path
     * @param zipEntryName entry name
     * @return decompressed entry stream
     * @throws IOException if the archive or entry cannot be opened
     */
    public static InputStream open(final Path path,
                                   final String zipEntryName)
            throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(zipEntryName);
            if (entry == null) {
                throw new IOException("ZIP entry not found: " + zipEntryName);
            }
            return new ZipFileInputStream(zipFile.getInputStream(entry),
                    zipFile);
        } catch (IOException exception) {
            try {
                zipFile.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    /**
     * Streams lines from a plain file, GZIP file, or first ZIP entry.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(final Path path) throws IOException {
        if (formatOf(path) == GCLogFileFormat.PLAIN_TEXT) {
            return Files.lines(path);
        }
        return lines(open(path));
    }

    /**
     * Streams lines from a named ZIP entry.
     *
     * @param path ZIP archive path
     * @param zipEntryName entry name
     * @return entry lines
     * @throws IOException if the archive or entry cannot be opened
     */
    public static Stream<String> lines(final Path path,
                                       final String zipEntryName)
            throws IOException {
        return lines(open(path, zipEntryName));
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), Charset.defaultCharset()));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class ZipFileInputStream extends FilterInputStream {

        /** Owning ZIP archive. */
        private final ZipFile zipFile;

        private ZipFileInputStream(final InputStream input,
                                   final ZipFile archive) {
            super(input);
            this.zipFile = archive;
        }

        @Override
        public void close() throws IOException {
            try (ZipFile ignored = zipFile) {
                super.close();
            }
        }
    }
}
