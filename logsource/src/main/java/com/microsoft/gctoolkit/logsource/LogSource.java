// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * IO operations common to file-backed GC log sources.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSource() {
    }

    /** Supported kinds of GC log source. */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /**
     * Discovers the source format from the filesystem and its magic bytes.
     *
     * @param path source path
     * @return discovered source format
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
     * Returns the source's on-disk size in bytes.
     *
     * @param path source path
     * @return size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long size(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Opens the readable content of a plain, ZIP, or GZIP source. For ZIP files,
     * the first non-directory entry is selected.
     *
     * @param path source path
     * @return stream positioned at the log content
     * @throws IOException if the source cannot be opened or has no readable ZIP entry
     */
    public static InputStream open(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to open directory as a log stream: " + path);
        }

        InputStream input = Files.newInputStream(path);
        try {
            if (format == Format.GZIP) {
                return new BufferedInputStream(new GZIPInputStream(input));
            }
            if (format == Format.ZIP) {
                ZipInputStream zip = new ZipInputStream(input);
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                    // Find the first file entry.
                }
                if (entry == null) {
                    zip.close();
                    throw new IOException("ZIP source contains no file entries: " + path);
                }
                return new BufferedInputStream(zip);
            }
            return new BufferedInputStream(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }
}
