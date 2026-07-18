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
 * A file-system source for a plain, ZIP, or GZIP log file.
 */
public final class LogFileSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private final Path path;
    private final Format format;

    private LogFileSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discovers the source format from its path and magic bytes.
     *
     * @param path source path
     * @return discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogFileSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogFileSource(path, Format.DIRECTORY);
        }
        if (hasMagic(path, GZIP_MAGIC_BYTE_1, GZIP_MAGIC_BYTE_2)) {
            return new LogFileSource(path, Format.GZIP);
        }
        if (hasMagic(path, ZIP_MAGIC_BYTE_1, ZIP_MAGIC_BYTE_2)) {
            return new LogFileSource(path, Format.ZIP);
        }
        return new LogFileSource(path, Format.PLAIN_TEXT);
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
     * Returns the source file size in bytes.
     *
     * @return physical source size
     * @throws IOException if the source size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens a line stream for a readable log source.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> stream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZipStream();
            case GZIP:
                return openGzipStream();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Tests the first two bytes of a file.
     *
     * @param path source path
     * @param first expected first byte
     * @param second expected second byte
     * @return whether the source starts with the expected bytes
     * @throws IOException if the source cannot be read
     */
    public static boolean hasMagic(Path path, int first, int second) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == first && input.read() == second;
        }
    }

    private Stream<String> openZipStream() throws IOException {
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

    private Stream<String> openGzipStream() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
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

    /**
     * Supported log source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
