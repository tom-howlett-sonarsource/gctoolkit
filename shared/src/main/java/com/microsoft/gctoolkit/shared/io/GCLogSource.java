// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens plain or compressed GC log sources.
 */
public final class GCLogSource {

    /** First magic byte for a GZIP source. */
    private static final int GZIP_MAGIC_BYTE_ONE = 0x1f;
    /** Second magic byte for a GZIP source. */
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8b;
    /** First magic byte for a ZIP source. */
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    /** Second magic byte for a ZIP source. */
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4b;

    private GCLogSource() {
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed text. */
        GZIP,
        /** File-system directory. */
        DIRECTORY
    }

    /**
     * Discover the source format from its file type and leading bytes.
     *
     * @param path source path
     * @return discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (size(path) < 2L) {
            return Format.PLAIN_TEXT;
        }
        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_ONE
                    && second == GZIP_MAGIC_BYTE_TWO) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_ONE && second == ZIP_MAGIC_BYTE_TWO) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Return the size of the source on disk in bytes.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public static long size(final Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open the source as a lazily read stream of lines. For ZIP sources, the
     * first non-directory entry is opened, matching the single-log behavior.
     * Closing the returned stream closes the underlying file.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(final Path path) throws IOException {
        Format format = discover(path);
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }

    private static Stream<String> zipLines(final Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
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

    private static Stream<String> gzipLines(final Path path)
            throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close() cannot report checked exceptions.
        }
    }
}
