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
 * A plain, ZIP, or GZIP log source.
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

    /**
     * Discover a log source's format from its magic bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Format format = Format.PLAIN_TEXT;
        if (Files.size(path) >= 2) {
            try (InputStream input = Files.newInputStream(path)) {
                int first = input.read();
                int second = input.read();
                if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                    format = Format.GZIP;
                } else if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                    format = Format.ZIP;
                }
            }
        }
        return new LogSource(path, format);
    }

    /**
     * @return the source path
     */
    public Path path() {
        return path;
    }

    /**
     * @return the source format
     */
    public Format format() {
        return format;
    }

    /**
     * Return the number of bytes occupied by the source file.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the source as a lazily read stream of lines. Closing the returned
     * stream closes all underlying file and decompression streams.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }

        InputStream input = format == Format.GZIP ? openGzip() : openZip();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openGzip() throws IOException {
        return new GZIPInputStream(Files.newInputStream(path));
    }

    private InputStream openZip() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Continue to the first file entry.
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
            // Stream.close cannot report checked exceptions.
        }
    }

    /** Supported log source formats. */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP
    }
}
