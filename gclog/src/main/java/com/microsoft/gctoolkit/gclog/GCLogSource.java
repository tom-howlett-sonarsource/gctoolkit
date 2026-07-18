// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

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
 * Discovers the format of a GC log source and provides common file operations.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover a GC log source from its path and content.
     *
     * @param path source path
     * @return discovered source
     * @throws IOException if the path cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_1 && secondByte == GZIP_MAGIC_2) {
                return new GCLogSource(path, Format.GZIP);
            }
            if (firstByte == ZIP_MAGIC_1 && secondByte == ZIP_MAGIC_2) {
                return new GCLogSource(path, Format.ZIP);
            }
            return new GCLogSource(path, Format.PLAIN_TEXT);
        }
    }

    /**
     * Return the source path.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Return the discovered source format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Return the size of the source file as stored on disk.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the contents of this source. ZIP sources expose the first file entry.
     *
     * @return source input stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case GZIP:
                return openGZip();
            case ZIP:
                return openFirstZipEntry();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream the contents of this source one line at a time.
     *
     * @return closeable line stream
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(openStream())));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return zipInput;
        } catch (IOException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private InputStream openGZip() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return new GZIPInputStream(input);
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
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
