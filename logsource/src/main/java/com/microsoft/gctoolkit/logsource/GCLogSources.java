// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File-system operations shared by GC log data sources.
 */
public final class GCLogSources {

    /** First GZIP signature byte. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    /** Second GZIP signature byte. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    /** First ZIP signature byte. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second ZIP signature byte. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private GCLogSources() {
    }

    /**
     * Discover the source format from the path and its leading bytes.
     *
     * @param path source path
     * @return the discovered source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
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
     * Return the physical size of a source on the file system.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the source cannot be sized
     */
    public static long byteSize(final Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open a line stream after discovering the source format. ZIP sources read
     * the first non-directory entry.
     *
     * @param path source path
     * @return a closeable stream of source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        return open(path, discover(path));
    }

    /**
     * Open a line stream with a previously discovered source format. ZIP
     * sources read the first non-directory entry.
     *
     * @param path source path
     * @param format previously discovered source format
     * @return a closeable stream of source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path path, final Format format)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path, StandardCharsets.UTF_8);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGzip(path);
            case DIRECTORY:
                throw new IOException("Unable to read directory " + path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> openZip(final Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(new BufferedInputStream(
                Files.newInputStream(path)));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                throw new IOException(
                        "ZIP source contains no file entries: " + path);
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

    private static Stream<String> openGzip(final Path path) throws IOException {
        InputStream input = new BufferedInputStream(Files.newInputStream(path));
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
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, Charset.defaultCharset()));
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
        /** Plain text file. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed file. */
        GZIP,
        /** File-system directory. */
        DIRECTORY
    }
}
