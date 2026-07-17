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
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File-system and compression utilities for GC log sources.
 */
public final class GCLogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_ONE = 0x1F;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8B;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4B;

    private GCLogSource() {
    }

    /**
     * Discover a source format from its file type and magic bytes.
     *
     * @param source path to inspect
     * @return discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(source)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_ONE
                    && secondByte == GZIP_MAGIC_BYTE_TWO) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_ONE
                    && secondByte == ZIP_MAGIC_BYTE_TWO) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Return the physical size of a source in bytes.
     *
     * @param source path to size
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public static long byteSize(final Path source) throws IOException {
        Path requiredSource = Objects.requireNonNull(source, "source");
        return Files.size(requiredSource);
    }

    /**
     * Open a plain, ZIP, or GZIP source as a stream of lines. For ZIP files,
     * the first non-directory entry is opened.
     *
     * @param source path to open
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openLines(final Path source)
            throws IOException {
        Format format = discover(source);
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(source);
            case ZIP:
                return openZipLines(source);
            case GZIP:
                return openGzipLines(source);
            default:
                throw new IOException("Unable to read " + source);
        }
    }

    private static Stream<String> openZipLines(final Path source)
            throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            input.close();
            return Stream.empty();
        }
        return lines(input);
    }

    private static Stream<String> openGzipLines(final Path source)
            throws IOException {
        return lines(new GZIPInputStream(Files.newInputStream(source)));
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
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** Uncompressed text file. */
        PLAIN_TEXT,
        /** File-system directory. */
        DIRECTORY
    }
}
