// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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
 * File-system and archive operations shared by GC log data sources.
 */
public final class GCLogSource {

    /** First byte in a GZIP header. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second byte in a GZIP header. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First byte in a ZIP header. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second byte in a ZIP header. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed file. */
        GZIP,
        /** Uncompressed text file. */
        PLAIN_TEXT,
        /** File-system directory. */
        DIRECTORY
    }

    /**
     * Discovers the source format from the path and, for files, its magic
     * bytes.
     *
     * @param source source path
     * @return discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(source)) {
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
     * Returns the number of bytes occupied by the source path.
     *
     * @param source source path
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(final Path source) throws IOException {
        return Files.size(Objects.requireNonNull(source, "source"));
    }

    /**
     * Opens the source as a stream of lines. ZIP sources use their first
     * non-directory entry.
     *
     * @param source source path
     * @return lazily read lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path source) throws IOException {
        return open(source, discover(source));
    }

    /**
     * Opens the source as a stream of lines using an already discovered format.
     * ZIP sources use their first non-directory entry.
     *
     * @param source source path
     * @param format source format
     * @return lazily read lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path source, final Format format)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(format, "format");
        switch (format) {
            case PLAIN_TEXT:
                return openPlain(source);
            case ZIP:
                return openZip(source);
            case GZIP:
                return openGzip(source);
            default:
                throw new IOException("Unable to read " + source);
        }
    }

    /**
     * Opens a plain-text source as a stream of lines.
     *
     * @param source source path
     * @return lazily read lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openPlain(final Path source)
            throws IOException {
        return Files.lines(Objects.requireNonNull(source, "source"));
    }

    /**
     * Opens a named entry in a ZIP source as a stream of lines.
     *
     * @param source ZIP source path
     * @param entryName entry to open
     * @return lazily read lines
     * @throws IOException if the source or entry cannot be opened
     */
    public static Stream<String> openZipEntry(final Path source,
                                              final String entryName)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(entryName, "entryName");

        ZipFile zipFile = new ZipFile(source.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to read ZIP entry " + entryName
                        + " from " + source);
            }
            BufferedReader reader = reader(zipFile.getInputStream(entry));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            try {
                zipFile.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    /**
     * Lists the non-directory entries in a ZIP source.
     *
     * @param source ZIP source path
     * @return entry names in archive order
     * @throws IOException if the source cannot be opened
     */
    public static List<String> zipEntries(final Path source)
            throws IOException {
        Path requiredSource = Objects.requireNonNull(source, "source");
        try (ZipFile zipFile = new ZipFile(requiredSource.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private static Stream<String> openZip(final Path source)
            throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());

            if (entry == null) {
                input.close();
                return Stream.empty();
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static Stream<String> openGzip(final Path source)
            throws IOException {
        InputStream input = Files.newInputStream(source);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = reader(input);
        return reader.lines().onClose(() -> close(reader));
    }

    private static BufferedReader reader(final InputStream input) {
        return new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
    }

    private static void close(final AutoCloseable... resources) {
        IOException failure = null;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }
}
