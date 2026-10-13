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

/**
 * A discovered log source and the common I/O operations supported for it.
 * ZIP sources represent the first non-directory entry, matching the historic
 * single-log behavior.
 */
public final class LogSource {
    private static final int GZIP_MAGIC = 0x1f8b;
    private static final int ZIP_MAGIC = 0x504b;

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /** Discover the source format using its type and magic bytes. */
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

    /** Open the uncompressed bytes represented by this source. */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN:
                return new BufferedInputStream(Files.newInputStream(path));
            case GZIP:
                return new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)));
            case ZIP:
                return openFirstZipEntry();
            default:
                throw new IOException("Unable to open log source " + path);
        }
    }

    /** Open the source as a lazily read stream of lines. */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(openStream()));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                return new BufferedInputStream(input);
            }
        }
        input.close();
        throw new IOException("ZIP log source contains no files: " + path);
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report a checked exception.
        }
    }

    public enum Format {
        PLAIN,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
