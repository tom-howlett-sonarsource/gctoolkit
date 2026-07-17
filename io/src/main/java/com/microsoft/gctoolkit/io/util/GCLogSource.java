// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

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
 * A discovered GC log source that can report its logical size and stream its lines.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1F;
    private static final int GZIP_MAGIC_2 = 0x8B;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4B;
    private static final int BUFFER_SIZE = 8192;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover the source format from the path and its leading bytes.
     *
     * @param path source path
     * @return discovered source
     * @throws IOException if the source cannot be read
     */
    public static GCLogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        int firstByte;
        int secondByte;
        try (InputStream input = Files.newInputStream(path)) {
            firstByte = input.read();
            secondByte = input.read();
        }

        if (firstByte == GZIP_MAGIC_1 && secondByte == GZIP_MAGIC_2) {
            return new GCLogSource(path, Format.GZIP);
        }
        if (firstByte == ZIP_MAGIC_1 && secondByte == ZIP_MAGIC_2) {
            return new GCLogSource(path, Format.ZIP);
        }
        return new GCLogSource(path, Format.PLAIN_TEXT);
    }

    /**
     * Test the first two bytes of a source.
     *
     * @param path source path
     * @param firstByte expected first byte
     * @param secondByte expected second byte
     * @return whether the bytes match
     */
    public static boolean hasMagic(Path path, int firstByte, int secondByte) {
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == firstByte && input.read() == secondByte;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Open a source using an already discovered format.
     *
     * @param path source path
     * @param format source format
     * @return stream of source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path path, Format format) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return lines(openFirstZipEntry(path));
            case GZIP:
                return lines(openGzip(path));
            case DIRECTORY:
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * @return discovered source format
     */
    public Format format() {
        return format;
    }

    /**
     * Return the number of uncompressed bytes read by {@link #lines()}.
     *
     * @return logical source size
     * @throws IOException if the source cannot be read
     */
    public long size() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.size(path);
            case ZIP:
                try (InputStream input = openFirstZipEntry(path)) {
                    return countBytes(input);
                }
            case GZIP:
                try (InputStream input = openGzip(path)) {
                    return countBytes(input);
                }
            case DIRECTORY:
            default:
                throw new IOException("Unable to size " + path);
        }
    }

    /**
     * Open the source as a stream of lines.
     *
     * @return stream of source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        return open(path, format);
    }

    private static ZipInputStream openFirstZipEntry(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
                input.closeEntry();
            }
            return input;
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static GZIPInputStream openGzip(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return new GZIPInputStream(input);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static long countBytes(InputStream input) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long size = 0L;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            size += bytesRead;
        }
        return size;
    }

    private static void close(BufferedReader reader) {
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
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
