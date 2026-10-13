// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
 * Discovers and opens file-system GC log sources.
 *
 * <p>ZIP sources expose their first non-directory entry, matching the historic
 * behavior of single GC and safepoint log sources.</p>
 */
public final class LogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSource() {
    }

    /** Identifies the supported source containers. */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /** Discover a source format by file type and magic bytes. */
    public static Format discover(Path path) throws IOException {
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

    /** Return the number of bytes occupied by the source on disk. */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /** Open a plain, ZIP, or GZIP source as a lazily read stream of lines. */
    public static Stream<String> open(Path path) throws IOException {
        Format format = discover(path);
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return lines(openFirstZipEntry(path));
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static InputStream openFirstZipEntry(Path path) throws IOException {
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

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot declare IOException.
        }
    }
}
