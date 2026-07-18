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
 * A file-system source containing GC log text.
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
     * Discovers the format of a GC log source.
     *
     * @param path source path
     * @return discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Path sourcePath = Objects.requireNonNull(path, "path");
        return new GCLogSource(sourcePath, discoverFormat(sourcePath));
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
     * @return source size
     * @throws IOException if the size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as a stream of lines. ZIP sources use the first
     * non-directory entry.
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
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no log file: " + path);
        }
        return lines(input);
    }

    private Stream<String> lines(InputStream input) {
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

    private static Format discoverFormat(Path path) throws IOException {
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
