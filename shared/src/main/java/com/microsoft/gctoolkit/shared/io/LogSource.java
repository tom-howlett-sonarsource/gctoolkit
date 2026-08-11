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

/** Discovers and opens a plain or compressed log source. */
public final class LogSource {
    private static final int GZIP_MAGIC1 = 0x1f;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private final Path path;
    private final Format format;
    private final long byteSize;

    private LogSource(Path path, Format format, long byteSize) {
        this.path = path;
        this.format = format;
        this.byteSize = byteSize;
    }

    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogSource(path, Format.DIRECTORY, 0L);
        }
        long size = Files.size(path);
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return new LogSource(path, Format.GZIP, size);
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return new LogSource(path, Format.ZIP, size);
            }
        }
        return new LogSource(path, Format.PLAIN_TEXT, size);
    }

    public static LogSource zip(Path path) throws IOException {
        return sourceOfFormat(path, Format.ZIP);
    }

    public static LogSource gzip(Path path) throws IOException {
        return sourceOfFormat(path, Format.GZIP);
    }

    private static LogSource sourceOfFormat(Path path, Format format) throws IOException {
        Objects.requireNonNull(path, "path");
        return new LogSource(path, format, Files.size(path));
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /** Returns the source's physical size, in bytes. Directories report zero. */
    public long byteSize() {
        return byteSize;
    }

    /** Opens the source as lines. The returned stream owns and closes its input. */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return gzipLines();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return readerLines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                throw new IOException("ZIP contains no log entries: " + path);
            }
            return readerLines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private Stream<String> readerLines(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
    }

    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
