// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

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
 * File-system operations shared by GC log source consumers.
 */
public final class LogSourceIO {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSourceIO() {
    }

    /** Supported kinds of log source. */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /**
     * Discovers a source's format from its file type and magic bytes.
     *
     * @param source source to inspect
     * @return discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(source)) {
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

    /**
     * Returns the source's size on disk in bytes.
     *
     * @param source source to size
     * @return physical byte size
     * @throws IOException if the size cannot be read
     */
    public static long size(Path source) throws IOException {
        return Files.size(Objects.requireNonNull(source, "source"));
    }

    /**
     * Opens the content of a plain, ZIP, or GZIP source. For ZIP files, the
     * first non-directory entry is opened.
     *
     * @param source source to open
     * @return content stream owned by the caller
     * @throws IOException if the source cannot be opened or has no file entry
     */
    public static InputStream open(Path source) throws IOException {
        Format format = discover(source);
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(source);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(source));
            case ZIP:
                return openZip(source);
            default:
                throw new IOException("Unable to open directory as a log stream: " + source);
        }
    }

    /**
     * Opens a source as a lazily read stream of lines.
     *
     * @param source source to open
     * @return line stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path source) throws IOException {
        Format format = discover(source);
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(source);
        }
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to open directory as a log stream: " + source);
        }
        InputStream input = format == Format.GZIP
                ? new GZIPInputStream(Files.newInputStream(source))
                : openZip(source);
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
    }

    private static InputStream openZip(Path source) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                return input;
            }
        }
        input.close();
        throw new IOException("ZIP log source contains no file entries: " + source);
    }
}
