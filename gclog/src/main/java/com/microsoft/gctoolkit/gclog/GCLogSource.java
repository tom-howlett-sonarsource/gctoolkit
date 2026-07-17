// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering and opening GC log sources.
 */
public final class GCLogSource {

    /** First byte in the GZIP file signature. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    /** Second byte in the GZIP file signature. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    /** First byte in the ZIP file signature. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second byte in the ZIP file signature. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private GCLogSource() {
    }

    /**
     * Discover the source type using the file contents rather than its name.
     *
     * @param path source path
     * @return discovered source type
     * @throws IOException when the source cannot be read
     */
    public static Type discover(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Type.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1
                    && secondByte == GZIP_MAGIC_BYTE_2) {
                return Type.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1
                    && secondByte == ZIP_MAGIC_BYTE_2) {
                return Type.ZIP;
            }
            return Type.PLAIN_TEXT;
        }
    }

    /**
     * Return the physical size of the source in bytes.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException when the source size cannot be read
     */
    public static long byteSize(final Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open the source as a stream of lines. For ZIP sources, the first
     * non-directory entry is opened.
     *
     * @param path source path
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        Type type = discover(path);
        switch (type) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGzip(path);
            case DIRECTORY:
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> openZip(final Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(input);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> openGzip(final Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException exception) {
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

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Supported GC log source types.
     */
    public enum Type {
        /** A ZIP archive. */
        ZIP,
        /** A GZIP-compressed file. */
        GZIP,
        /** An uncompressed text file. */
        PLAIN_TEXT,
        /** A directory containing log files. */
        DIRECTORY
    }
}
