// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * IO operations shared by GC log consumers.
 */
public final class LogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    /** Source path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    /**
     * Creates a source and discovers its format.
     *
     * @param sourcePath source path
     * @throws IOException if the source cannot be inspected
     */
    public LogSource(final Path sourcePath) throws IOException {
        this.path = Objects.requireNonNull(sourcePath);
        this.format = discover(sourcePath);
    }

    /**
     * Returns the source path.
     *
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the discovered source format.
     *
     * @return source format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Returns the physical size of the source in bytes.
     *
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public long size() throws IOException {
        return byteSize(path);
    }

    /**
     * Opens the source as a stream of lines.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> stream() throws IOException {
        return open(path, format);
    }

    /**
     * Discovers a source format from its path and magic bytes.
     *
     * @param path source path
     * @return discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(final Path path) throws IOException {
        Objects.requireNonNull(path);
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the physical size of a source in bytes.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public static long byteSize(final Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path));
    }

    /**
     * Discovers and opens a source as a stream of lines.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        return open(path, discover(path));
    }

    private static Stream<String> open(
            final Path path, final Format format) throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> openZip(final Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());

        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no log file: " + path);
        }
        return lines(input);
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        /** Plain text source. */
        PLAIN_TEXT,
        /** ZIP-compressed source. */
        ZIP,
        /** GZIP-compressed source. */
        GZIP,
        /** Directory source. */
        DIRECTORY
    }
}
