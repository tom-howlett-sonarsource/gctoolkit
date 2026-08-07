// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a single GC log source. ZIP sources use the first
 * non-directory entry, matching the single-log behavior of the API.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSource() {
    }

    /** Supported source formats. */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /**
     * Discover a source format from the filesystem and its magic bytes.
     *
     * @param path source path
     * @return discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
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

    /**
     * Return the number of uncompressed bytes exposed by this source.
     *
     * @param path source path
     * @return uncompressed byte count
     * @throws IOException if the source cannot be read
     */
    public static long sizeInBytes(Path path) throws IOException {
        try (InputStream input = open(path)) {
            long size = 0L;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
            }
            return size;
        }
    }

    /**
     * Open the uncompressed bytes of a plain, ZIP, or GZIP source.
     *
     * @param path source path
     * @return input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAIN_TEXT) {
            return new BufferedInputStream(Files.newInputStream(path));
        }
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                // Find the first file entry.
            }
            if (entry == null) {
                zip.close();
                throw new IOException("ZIP source contains no file entries: " + path);
            }
            return zip;
        }
        throw new IOException("Unable to read directory as a single log source: " + path);
    }

    /**
     * Open a source as UTF-8 lines. Closing the stream closes the source.
     *
     * @param path source path
     * @return stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path path) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(open(path), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked exceptions.
        }
    }
}
