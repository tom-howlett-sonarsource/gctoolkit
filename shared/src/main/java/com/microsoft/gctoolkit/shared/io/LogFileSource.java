// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
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
import java.util.zip.ZipInputStream;

/**
 * Common file-system operations for log sources.
 */
public final class LogFileSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogFileSource() {
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed file. */
        GZIP,
        /** Directory source. */
        DIRECTORY
    }

    /**
     * Discover the source format from the file itself rather than its name.
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
     * Return the number of bytes occupied by a source. Directory sources are
     * measured as the sum of their regular files.
     *
     * @param source source path
     * @return source size in bytes
     * @throws IOException if the source cannot be measured
     */
    public static long byteSize(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (!Files.isDirectory(source)) {
            return Files.size(source);
        }

        try (Stream<Path> paths = Files.walk(source)) {
            try {
                return paths.filter(Files::isRegularFile)
                        .mapToLong(LogFileSource::fileSize)
                        .sum();
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    /**
     * Open a source as a stream of lines. ZIP sources use their first
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
     * Open a source with an already discovered format.
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
                return Files.lines(source);
            case ZIP:
                return openZip(source);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(source)));
            case DIRECTORY:
                throw new IOException(
                        "Unable to open directory log source " + source);
            default:
                throw new IOException("Unable to read " + source);
        }
    }

    /**
     * List the non-directory entries in a ZIP source.
     *
     * @param source ZIP source path
     * @return entry names in archive order
     * @throws IOException if the source cannot be opened
     */
    public static List<String> zipEntries(final Path source)
            throws IOException {
        Objects.requireNonNull(source, "source");
        try (ZipFile zipFile = new ZipFile(source.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open one named entry from a ZIP source as a stream of lines.
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

        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && !entryName.equals(entry.getName()));
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to find ZIP entry " + entryName
                        + " in " + source);
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static long fileSize(final Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
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
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input),
                        StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
