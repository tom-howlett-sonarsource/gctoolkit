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

/** A file-system source for plain, ZIP, or GZIP GC log content. */
public final class LogSource {
    private static final int GZIP_MAGIC = 0x1f8b;
    private static final int ZIP_MAGIC = 0x504b;

    private final Path path;
    private final Format format;

    private LogSource(Path path) throws IOException {
        this.path = Objects.requireNonNull(path, "path");
        this.format = discover(path);
    }

    public static LogSource from(Path path) throws IOException {
        return new LogSource(path);
    }

    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path))
            return Format.DIRECTORY;
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            int magic = first << 8 | second;
            if (magic == GZIP_MAGIC)
                return Format.GZIP;
            if (magic == ZIP_MAGIC)
                return Format.ZIP;
            return Format.PLAIN;
        }
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /** Returns the physical size of the source file in bytes. */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /** Opens the source content, decompressing it when necessary. */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN: return Files.newInputStream(path);
            case GZIP: return new GZIPInputStream(Files.newInputStream(path));
            case ZIP: return openFirstZipEntry();
            default: throw new IOException("Unable to read " + path);
        }
    }

    /** Opens the source content as a lazily read stream of lines. */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(openStream())));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Find the first file, preserving the historical single-log behavior.
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + path);
        }
        return input;
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked close failures.
        }
    }

    public enum Format {
        PLAIN,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
