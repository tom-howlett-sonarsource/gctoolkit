// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
 * A discovered GC log source and the common IO operations for it.
 * ZIP sources expose their first non-directory entry, matching the historical
 * single-log behavior in the API and parser modules.
 */
public final class LogSource {
    private static final int GZIP_MAGIC = 0x1f8b;
    private static final int ZIP_MAGIC = 0x504b;

    public enum Format { PLAIN, ZIP, GZIP, DIRECTORY }

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /** Discovers the source format from the filesystem and its magic bytes. */
    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogSource(path, Format.DIRECTORY);
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            int magic = first << 8 | second;
            if (magic == GZIP_MAGIC) {
                return new LogSource(path, Format.GZIP);
            }
            if (magic == ZIP_MAGIC) {
                return new LogSource(path, Format.ZIP);
            }
            return new LogSource(path, Format.PLAIN);
        }
    }

    public Path path() { return path; }

    public Format format() { return format; }

    /** Returns the number of readable (uncompressed) bytes in this source. */
    public long byteSize() throws IOException {
        if (format == Format.PLAIN) {
            return Files.size(path);
        }
        if (format == Format.DIRECTORY) {
            throw new IOException("Cannot size directory log source " + path);
        }
        try (InputStream input = open()) {
            long size = 0;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
            }
            return size;
        }
    }

    /** Opens the readable content of this source. */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openFirstZipEntry(path);
            default:
                throw new IOException("Cannot open directory log source " + path);
        }
    }

    /** Opens the source as a lazily read stream of lines. */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(open())));
        return reader.lines().onClose(() -> close(reader));
    }

    private static InputStream openFirstZipEntry(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Find the first actual log entry.
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP log source contains no file entries: " + path);
        }
        return input;
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked IO failures.
        }
    }
}
