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
 * A discovered GC log source backed by a plain, ZIP, or GZIP file.
 */
public final class GCLogSource {

    /** First byte of a GZIP file signature. */
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    /** Second byte of a GZIP file signature. */
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    /** First byte of a ZIP file signature. */
    private static final int ZIP_MAGIC_FIRST = 0x50;
    /** Second byte of a ZIP file signature. */
    private static final int ZIP_MAGIC_SECOND = 0x4b;
    /** Buffer size used when counting uncompressed bytes. */
    private static final int SIZE_BUFFER_LENGTH = 8192;

    /** Source path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private GCLogSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discover the format of the source at {@code path}.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the path cannot be inspected
     */
    public static GCLogSource discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return new GCLogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
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
     * Return the number of uncompressed bytes in the opened log source.
     * For ZIP files this is the size of the first non-directory entry.
     *
     * @return logical source size in bytes
     * @throws IOException if the source cannot be read
     */
    public long byteSize() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.size(path);
        }
        if (format == Format.DIRECTORY) {
            throw unsupportedFormat();
        }

        try (InputStream input = open()) {
            byte[] buffer = new byte[SIZE_BUFFER_LENGTH];
            long size = 0L;
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                size += bytesRead;
            }
            return size;
        }
    }

    /**
     * Open the uncompressed bytes of this source. ZIP sources expose the first
     * non-directory entry.
     *
     * @return an input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case ZIP:
                return openZip();
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            default:
                throw unsupportedFormat();
        }
    }

    /**
     * Open the source as UTF-8 lines. Closing the returned stream closes the
     * underlying file or archive stream.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(open(), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openZip() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                throw new IOException(
                        "ZIP source contains no file entries: " + path);
            }
            return input;
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private IOException unsupportedFormat() {
        return new IOException("Unable to open GC log source " + path
                + " with format " + format);
    }

    private static void close(final BufferedReader reader) {
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
        /** Uncompressed text file. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP file. */
        GZIP,
        /** Directory containing log files. */
        DIRECTORY
    }
}
