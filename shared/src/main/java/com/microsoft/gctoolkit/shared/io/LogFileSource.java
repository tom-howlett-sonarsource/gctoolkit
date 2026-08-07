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
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens file-system log sources.
 */
public final class LogFileSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogFileSource() {
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /**
     * Discover the source format from its file-system type and magic bytes.
     *
     * @param path source path
     * @return discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (size(path) < 2) {
            return Format.PLAIN_TEXT;
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

    /**
     * Return the physical size of a source in bytes.
     *
     * @param path source path
     * @return byte size
     * @throws IOException if the size cannot be read
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a plain, ZIP, or GZIP source as a lazily read stream of lines. For
     * ZIP files, the first non-directory entry is selected.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened or is a directory
     */
    public static Stream<String> openLines(Path path) throws IOException {
        Format format = discover(path);
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZipLines(path);
            case GZIP:
                return readerLines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> openZipLines(Path path) throws IOException {
        ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipInput.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipInput.close();
            return Stream.empty();
        }
        return readerLines(zipInput);
    }

    private static Stream<String> readerLines(InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked IO failures.
        }
    }
}
