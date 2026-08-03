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
 * A file-backed log source whose representation is discovered from its content.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;

    /**
     * Creates a source and discovers its representation.
     *
     * @param path source path
     * @throws IOException if the source cannot be inspected
     */
    public LogSource(Path path) throws IOException {
        this.path = Objects.requireNonNull(path, "path");
        this.format = discover(path);
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /**
     * Returns the number of bytes occupied by the source file.
     *
     * @return source size in bytes
     * @throws IOException if its size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source contents, decompressing GZIP or the first file in a ZIP.
     * The caller owns the returned stream.
     *
     * @return an open content stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openZip();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens the source as a lazily read stream of lines.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(open()))).lines();
    }

    private InputStream openZip() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Find the first file entry.
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + path);
        }
        return input;
    }

    private static Format discover(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
