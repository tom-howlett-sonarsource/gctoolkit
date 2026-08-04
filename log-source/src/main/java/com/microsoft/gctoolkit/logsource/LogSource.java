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
 * A discovered GC log source. A source can be plain text, ZIP, GZIP, or a
 * directory. ZIP sources expose the first non-directory entry, matching the
 * single-log behavior used by GCToolKit.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    /** The physical format of a log source. */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    private final Path path;
    private final Format format;
    private final long size;

    private LogSource(Path path, Format format, long size) {
        this.path = path;
        this.format = format;
        this.size = size;
    }

    /**
     * Discover the source format from the path and its magic bytes.
     *
     * @param path path to a GC log source
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogSource(path, Format.DIRECTORY, 0L);
        }

        long size = Files.size(path);
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return new LogSource(path, Format.GZIP, size);
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return new LogSource(path, Format.ZIP, size);
            }
            return new LogSource(path, Format.PLAIN_TEXT, size);
        }
    }

    /** @return the source path */
    public Path path() {
        return path;
    }

    /** @return the discovered source format */
    public Format format() {
        return format;
    }

    /** @return the on-disk source size in bytes, or zero for a directory */
    public long size() {
        return size;
    }

    /**
     * Open the source content. ZIP input is positioned at the first
     * non-directory entry.
     *
     * @return an input stream for the decompressed log content
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case ZIP:
                return openZip();
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            default:
                throw new IOException("Unable to open directory as a log source: " + path);
        }
    }

    /**
     * Stream the source content one line at a time.
     *
     * @return a stream of log lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(open())));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openZip() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return input;
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
