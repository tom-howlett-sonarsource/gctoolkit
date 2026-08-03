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

/** IO operations shared by GC log consumers. */
public final class LogSource {
    private static final int GZIP_MAGIC1 = 0x1f;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSource() {
    }

    public enum Format {
        PLAIN_TEXT, ZIP, GZIP, DIRECTORY
    }

    /** Discovers the source format from its type and magic bytes. */
    public static Format format(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /** Returns the source's size in bytes. */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /** Opens a plain, gzip, or the first non-directory ZIP entry. */
    public static InputStream open(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            Format format = format(path);
            if (format == Format.GZIP) {
                return new GZIPInputStream(input);
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
                return zip;
            }
            if (format == Format.PLAIN_TEXT) {
                return input;
            }
            throw new IOException("Unable to open directory as a log stream: " + path);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    /** Opens a lazily read line stream that closes its underlying source. */
    public static Stream<String> lines(Path path) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(open(path))));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Stream.close cannot report checked IO failures.
            }
        });
    }
}
