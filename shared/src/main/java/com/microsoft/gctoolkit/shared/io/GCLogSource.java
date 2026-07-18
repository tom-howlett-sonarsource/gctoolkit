// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A readable GC log source backed by a plain file, GZIP file, or ZIP entry.
 */
public final class GCLogSource {

    /** GZIP first magic byte. */
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    /** GZIP second magic byte. */
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    /** ZIP first magic byte. */
    private static final int ZIP_MAGIC_FIRST = 0x50;
    /** ZIP second magic byte. */
    private static final int ZIP_MAGIC_SECOND = 0x4b;
    /** Buffer size used when counting decompressed bytes. */
    private static final int BYTE_BUFFER_SIZE = 8192;

    /** Source file path. */
    private final Path path;
    /** Detected source format. */
    private final Format format;
    /** ZIP entry name, or {@code null} for non-ZIP sources. */
    private final String zipEntryName;
    /** Known uncompressed size, or {@code -1} when it must be counted. */
    private final long knownByteSize;

    private GCLogSource(final Path sourcePath, final Format sourceFormat,
                        final String entryName, final long sourceByteSize) {
        this.path = sourcePath;
        this.format = sourceFormat;
        this.zipEntryName = entryName;
        this.knownByteSize = sourceByteSize;
    }

    /**
     * Discover the readable sources represented by a path. ZIP archives produce
     * one source for each non-directory entry; other files produce one source.
     *
     * @param path source path
     * @return discovered readable sources
     * @throws IOException when the path cannot be inspected
     */
    public static List<GCLogSource> discover(final Path path)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Format format = formatOf(path);
        if (format == Format.DIRECTORY) {
            throw new IOException(
                    "A directory is not a GC log source: " + path);
        }
        if (format != Format.ZIP) {
            final long byteSize = format == Format.PLAIN_TEXT
                    ? Files.size(path)
                    : -1L;
            return List.of(new GCLogSource(path, format, null, byteSize));
        }

        List<GCLogSource> sources = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .forEach(entry -> sources.add(new GCLogSource(
                            path, Format.ZIP, entry.getName(),
                            entry.getSize())));
        }
        return List.copyOf(sources);
    }

    /**
     * Return the first readable source represented by a path.
     *
     * @param path source path
     * @return first readable source
     * @throws IOException when no readable source exists
     */
    public static GCLogSource first(final Path path) throws IOException {
        List<GCLogSource> sources = discover(path);
        if (sources.isEmpty()) {
            throw new IOException("No readable log entries in " + path);
        }
        return sources.get(0);
    }

    /**
     * Detect the source format from its path and magic bytes.
     *
     * @param path source path
     * @return detected format
     * @throws IOException when the path cannot be inspected
     */
    public static Format formatOf(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Open the uncompressed bytes for this source. Closing the returned stream
     * also closes any archive resources opened for it.
     *
     * @return uncompressed source stream
     * @throws IOException when the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.newInputStream(path);
        }
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            return openZipEntry();
        }
        throw new IOException(
                "A directory cannot be opened as a GC log source: " + path);
    }

    /**
     * Stream source lines using the platform default charset for compressed
     * logs. Closing the line stream closes its underlying file or archive.
     *
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        return lines(openStream());
    }

    private static Stream<String> lines(final InputStream input) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, Charset.defaultCharset()));
            return reader.lines().onClose(() -> close(reader));
        } catch (RuntimeException exception) {
            close(input, exception);
            throw exception;
        }
    }

    /**
     * Return the number of uncompressed bytes in this source.
     *
     * @return uncompressed byte size
     * @throws IOException when the source cannot be read
     */
    public long byteSize() throws IOException {
        if (knownByteSize >= 0L) {
            return knownByteSize;
        }
        long size = 0L;
        byte[] buffer = new byte[BYTE_BUFFER_SIZE];
        try (InputStream input = openStream()) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                size += bytesRead;
            }
        }
        return size;
    }

    /**
     * @return backing file path
     */
    public Path path() {
        return path;
    }

    /**
     * @return ZIP entry name or backing file name
     */
    public String name() {
        if (zipEntryName != null) {
            return zipEntryName;
        }
        final Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    /**
     * @return detected source format
     */
    public Format format() {
        return format;
    }

    private InputStream openZipEntry() throws IOException {
        final ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(zipEntryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException("ZIP entry not found: " + zipEntryName);
        }
        try {
            InputStream input = zipFile.getInputStream(entry);
            return new FilterInputStream(input) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        zipFile.close();
                    }
                }
            };
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void close(final InputStream input,
                              final RuntimeException original) {
        try {
            input.close();
        } catch (IOException closeFailure) {
            original.addSuppressed(closeFailure);
        }
    }

    public enum Format {
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed file. */
        GZIP,
        /** Uncompressed text file. */
        PLAIN_TEXT,
        /** Directory path. */
        DIRECTORY
    }
}
