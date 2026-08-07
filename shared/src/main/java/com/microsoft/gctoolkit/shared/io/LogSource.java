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
 * Common file-system operations for plain and compressed log sources.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private LogSource() {
    }

    /** Describes the physical form of a log source. */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    /**
     * Discovers a source's format from its type and magic bytes.
     *
     * @param path source path
     * @return the discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (byteSize(path) < 2) {
            return Format.PLAIN_TEXT;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the physical size of a source in bytes.
     *
     * @param path source path
     * @return byte size
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Opens a plain, ZIP, or GZIP source as a stream of lines. For a ZIP source,
     * the first non-directory entry is used.
     *
     * @param path source path
     * @return lazily read lines; closing the stream closes its source
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path path) throws IOException {
        Format format = discover(path);
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                input.close();
                throw new IOException("ZIP source contains no log entries: " + path);
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report a checked exception.
        }
    }
}
