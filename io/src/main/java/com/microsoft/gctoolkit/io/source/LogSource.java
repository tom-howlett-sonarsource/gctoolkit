// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
 * A discoverable source of GC log lines.
 */
public final class LogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC1 = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC2 = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC2 = 0x4b;

    /** Source filesystem path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private LogSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discovers the source type from the filesystem and its magic bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogSource discover(final Path path) throws IOException {
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
     * Returns the physical size of the source in bytes.
     *
     * @return byte size
     * @throws IOException if the size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as lines. For ZIP sources, the first non-directory
     * entry is used.
     * Closing the returned stream closes every underlying IO resource.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return compressedLines(openFirstZipEntry());
            case GZIP:
                return compressedLines(openGzip());
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        boolean opened = false;
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    opened = true;
                    return input;
                }
            }
            throw new IOException(
                    "ZIP source contains no file entries: " + path);
        } finally {
            if (!opened) {
                input.close();
            }
        }
    }

    private InputStream openGzip() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return new GZIPInputStream(input);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> compressedLines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked IO failures.
        }
    }

    public enum Format {
        /** An uncompressed text file. */
        PLAIN_TEXT,
        /** A ZIP archive. */
        ZIP,
        /** A GZIP-compressed file. */
        GZIP,
        /** A filesystem directory. */
        DIRECTORY
    }
}
