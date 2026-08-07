// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a plain or compressed GC log source.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC1 = 0x1f;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discovers the source format from its path and magic bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        try (var input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return new GCLogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return new GCLogSource(path, Format.ZIP);
            }
            return new GCLogSource(path, Format.PLAIN_TEXT);
        }
    }

    public Path getPath() {
        return path;
    }

    public Format getFormat() {
        return format;
    }

    /**
     * Returns the number of bytes occupied by the source file.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as a stream of lines. For ZIP sources, the first
     * non-directory entry is opened.
     *
     * @return lines from the source
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZip();
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Stream<String> openZip() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                input.close();
                throw new IOException("ZIP source contains no log file: " + path);
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(java.io.InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Stream.close cannot report checked IO failures.
            }
        });
    }

    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
