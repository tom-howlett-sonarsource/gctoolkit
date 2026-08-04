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
 * File-system operations shared by GC log sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private GCLogSource() {
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

    /**
     * Discover a source's format from its file-system type and magic bytes.
     *
     * @param source source path
     * @return the discovered format
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
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Return the physical size of a source file.
     *
     * @param source source path
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long sizeInBytes(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        return Files.size(source);
    }

    /**
     * Open the content stream for a plain, ZIP, or GZIP source. For a ZIP source,
     * the first non-directory entry is selected.
     *
     * @param source source path
     * @return the opened content stream
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(Path source) throws IOException {
        return open(source, discover(source));
    }

    /**
     * Stream lines from a plain, ZIP, or GZIP source. For a ZIP source, the first
     * non-directory entry is selected.
     *
     * @param source source path
     * @return a lazily read stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path source) throws IOException {
        Format format = discover(source);
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(source);
        }

        InputStream input = open(source, format);
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static InputStream open(Path source, Format format) throws IOException {
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to read " + source);
        }

        InputStream input = Files.newInputStream(source);
        try {
            if (format == Format.GZIP) {
                return new GZIPInputStream(input);
            }
            if (format == Format.ZIP) {
                return firstFileEntry(new ZipInputStream(input));
            }
            return input;
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static InputStream firstFileEntry(ZipInputStream input) throws IOException {
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return input;
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
