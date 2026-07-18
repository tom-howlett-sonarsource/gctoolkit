// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A file-system source containing plain-text or compressed GC log data.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_FIRST_BYTE = 0x1f;
    private static final int GZIP_MAGIC_SECOND_BYTE = 0x8b;
    private static final int ZIP_MAGIC_FIRST_BYTE = 0x50;
    private static final int ZIP_MAGIC_SECOND_BYTE = 0x4b;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discovers the source format from the path and its magic bytes.
     *
     * @param path source path
     * @return discovered GC log source
     */
    public static GCLogSource from(Path path) {
        Objects.requireNonNull(path, "path");
        return new GCLogSource(path, discoverFormat(path));
    }

    /**
     * Returns the source path.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the discovered source format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the number of bytes occupied by the source file.
     *
     * @return source byte size
     * @throws IOException when the size cannot be read
     */
    public long sizeInBytes() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source contents. ZIP sources expose the first file entry.
     *
     * @return open source stream
     * @throws IOException when the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openZip();
            case DIRECTORY:
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens the source as a stream of lines.
     *
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(open()), StandardCharsets.UTF_8));
        try {
            return reader.lines().onClose(() -> close(reader));
        } catch (RuntimeException exception) {
            close(reader);
            throw exception;
        }
    }

    private static Format discoverFormat(Path path) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_FIRST_BYTE && secondByte == GZIP_MAGIC_SECOND_BYTE) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_FIRST_BYTE && secondByte == ZIP_MAGIC_SECOND_BYTE) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        } catch (IOException exception) {
            return Format.PLAIN_TEXT;
        }
    }

    private InputStream openZip() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return input;
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
