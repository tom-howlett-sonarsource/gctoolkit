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

/** A discovered plain, ZIP, GZIP, or directory GC log source. */
public final class LogSource {
    private static final int GZIP_MAGIC1 = 0x1f;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /** Discovers a source's format from its path and magic bytes. */
    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogSource(path, Format.DIRECTORY);
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return new LogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return new LogSource(path, Format.ZIP);
            }
            return new LogSource(path, Format.PLAIN_TEXT);
        }
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /** Returns the readable content size, expanding compressed input. */
    public long byteSize() throws IOException {
        if (format == Format.DIRECTORY) {
            try (Stream<Path> entries = Files.list(path)) {
                long size = 0;
                for (Path entry : (Iterable<Path>) entries.filter(Files::isRegularFile)::iterator) {
                    size += Files.size(entry);
                }
                return size;
            }
        }
        try (InputStream input = openStream()) {
            byte[] buffer = new byte[8192];
            long size = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
            }
            return size;
        }
    }

    /** Opens the source, using the first non-directory ZIP entry. */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openZipStream();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /** Streams decoded lines and closes the underlying source when closed. */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(openStream())));
        return reader.lines().onClose(LogSource.close(reader));
    }

    private InputStream openZipStream() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Find the first file entry.
        }
        return input;
    }

    private static Runnable close(BufferedReader reader) {
        return () -> {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Stream.close cannot report an IOException.
            }
        };
    }

    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
