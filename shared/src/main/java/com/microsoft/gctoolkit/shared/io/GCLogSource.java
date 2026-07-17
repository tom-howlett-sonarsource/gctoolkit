// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

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
 * Discovers and opens a GC log source.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover the format of a GC log source.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Path sourcePath = Objects.requireNonNull(path, "path");
        if (Files.isDirectory(sourcePath)) {
            return new GCLogSource(sourcePath, Format.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(sourcePath)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1 && secondByte == GZIP_MAGIC_BYTE_2) {
                return new GCLogSource(sourcePath, Format.GZIP);
            }
            if (firstByte == ZIP_MAGIC_BYTE_1 && secondByte == ZIP_MAGIC_BYTE_2) {
                return new GCLogSource(sourcePath, Format.ZIP);
            }
            return new GCLogSource(sourcePath, Format.PLAIN_TEXT);
        }
    }

    /**
     * Return the path backing this source.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Return the source size in bytes.
     *
     * @return source byte size
     * @throws IOException if the source size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the source as a stream of lines.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return readerLines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    public boolean isPlainText() {
        return format == Format.PLAIN_TEXT;
    }

    public boolean isZip() {
        return format == Format.ZIP;
    }

    public boolean isGZip() {
        return format == Format.GZIP;
    }

    public boolean isDirectory() {
        return format == Format.DIRECTORY;
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                input.close();
                return Stream.empty();
            }
            return readerLines(input);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> readerLines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
