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

/** File-system operations common to GC log sources. */
public final class LogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSource() {
    }

    /** Supported source formats. */
    public enum Format { PLAIN_TEXT, ZIP, GZIP, DIRECTORY }

    /** Discovers a source format using its type and magic bytes. */
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

    /** Returns the source's on-disk byte size. */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /** Opens a line stream, transparently reading plain, ZIP, or GZIP input. */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, discover(path));
    }

    /** Opens a line stream using a previously discovered format. */
    public static Stream<String> lines(Path path, Format format) throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return readerLines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Find the first file, matching the historical single-source behavior.
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + path);
        }
        return readerLines(input);
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
            // Stream.close cannot report checked close failures.
        }
    }
}
