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
 * Discovers and opens a GC log source. ZIP sources expose the first
 * non-directory entry, matching the single-log behavior of the existing API.
 */
public final class LogFileSource {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private final Path path;
    private final Format format;

    private LogFileSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover the source format from the path and its magic bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogFileSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogFileSource(path, Format.DIRECTORY);
        }
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return new LogFileSource(path, Format.GZIP);
        }
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return new LogFileSource(path, Format.ZIP);
        }
        return new LogFileSource(path, Format.PLAIN_TEXT);
    }

    /**
     * Test the first two bytes of a source.
     *
     * @param path source path
     * @param first expected first byte
     * @param second expected second byte
     * @return {@code true} when both bytes match
     * @throws IOException if the bytes cannot be read
     */
    public static boolean hasMagic(Path path, int first, int second) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            return input.read() == first && input.read() == second;
        }
    }

    /**
     * Return the number of bytes occupied by a source on disk.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * @return the source path
     */
    public Path path() {
        return path;
    }

    /**
     * @return the discovered source format
     */
    public Format format() {
        return format;
    }

    /**
     * @return the number of bytes occupied by this source on disk
     * @throws IOException if the source size cannot be read
     */
    public long sizeInBytes() throws IOException {
        return sizeInBytes(path);
    }

    /**
     * Open the source as a lazy stream of lines.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return plainTextLines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a plain-text source as lines.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open the first non-directory entry in a ZIP source as lines.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(Objects.requireNonNull(path, "path")));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return bufferedLines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    /**
     * Open a GZIP source as lines.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        InputStream input = Files.newInputStream(Objects.requireNonNull(path, "path"));
        try {
            return bufferedLines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> bufferedLines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Source format supported by the shared reader. */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
