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
 * A file-system GC log source and the common IO operations performed on it.
 */
public final class LogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /** Discover the source format from the path and its magic bytes. */
    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogSource(path, Format.DIRECTORY);
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return new LogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return new LogSource(path, Format.ZIP);
            }
        }
        return new LogSource(path, Format.PLAIN_TEXT);
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /** Return the number of bytes occupied by the source file. */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /** Open the plain file or the first non-directory member of a ZIP/GZIP source as lines. */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return lines(openFirstZipEntry());
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Advance to the first file entry.
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no file entries: " + path);
        }
        return input;
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked IO failures.
        }
    }

    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
