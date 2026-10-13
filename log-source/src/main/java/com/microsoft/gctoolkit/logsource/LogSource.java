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

/** A discovered plain, ZIP, or GZIP GC log source. */
public final class LogSource {
    private static final int GZIP_MAGIC_ONE = 0x1f;
    private static final int GZIP_MAGIC_TWO = 0x8b;
    private static final int ZIP_MAGIC_ONE = 0x50;
    private static final int ZIP_MAGIC_TWO = 0x4b;

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /** Discovers the source format from its contents, rather than its suffix. */
    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path))
            return new LogSource(path, Format.DIRECTORY);
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_ONE && second == GZIP_MAGIC_TWO)
                return new LogSource(path, Format.GZIP);
            if (first == ZIP_MAGIC_ONE && second == ZIP_MAGIC_TWO)
                return new LogSource(path, Format.ZIP);
            return new LogSource(path, Format.PLAIN);
        }
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /** Returns the source's on-disk byte size. */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    public boolean isReadable() {
        return format == Format.PLAIN || format == Format.ZIP || format == Format.GZIP;
    }

    /** Opens the uncompressed bytes of the source (the first file entry for ZIP). */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openFirstZipEntry();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /** Opens the source as a lazily read stream of lines. */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(open())));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Skip directory entries.
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no file entries: " + path);
        }
        return input;
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report an IOException.
        }
    }

    public enum Format {
        PLAIN,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
