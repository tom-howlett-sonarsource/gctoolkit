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
 * IO operations shared by production GC log sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Discover the source format from the path and its magic bytes.
     *
     * @param path source path
     * @return discovered format
     */
    public static Format discover(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
        } catch (IOException ignored) {
            // Preserve the existing behavior of treating an unreadable, non-directory path as plain text.
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Return the number of uncompressed bytes in the readable log content.
     * For ZIP files, this is the size of the first non-directory entry.
     *
     * @param path source path
     * @return uncompressed byte count
     * @throws IOException if the source cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAIN_TEXT) {
            return Files.size(path);
        }
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to size directory " + path);
        }

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
     * Open the readable log content. ZIP sources are positioned at their first
     * non-directory entry.
     *
     * @param path source path
     * @return input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
            try {
                ZipEntry entry;
                do {
                    entry = zip.getNextEntry();
                } while (entry != null && entry.isDirectory());
                return zip;
            } catch (IOException exception) {
                zip.close();
                throw exception;
            }
        }
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to read directory " + path);
        }
        return Files.newInputStream(path);
    }

    /**
     * Open the readable log content as lines.
     *
     * @param path source path
     * @return lazily read lines; closing the stream closes the source
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path path) throws IOException {
        if (discover(path) == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(open(path))));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked exceptions.
        }
    }

    /** Supported GC log source formats. */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
