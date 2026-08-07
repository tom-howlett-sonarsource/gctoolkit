// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File-system operations common to GC log data sources.
 */
public final class LogSource {

    /** First byte in a GZIP signature. */
    private static final int GZIP_MAGIC1 = 0x1F;
    /** Second byte in a GZIP signature. */
    private static final int GZIP_MAGIC2 = 0x8B;
    /** First byte in a ZIP signature. */
    private static final int ZIP_MAGIC1 = 0x50;
    /** Second byte in a ZIP signature. */
    private static final int ZIP_MAGIC2 = 0x4B;

    private LogSource() {
    }

    /**
     * Identifies a source by inspecting its file type and leading bytes.
     * Unreadable paths retain the historical plain-text classification.
     *
     * @param path source path
     * @return detected source format
     */
    public static Format discover(final Path path) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (var input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return Format.ZIP;
            }
        } catch (IOException ignored) {
            // Preserve the former behavior: IO failures classify as plain text.
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Returns the on-disk byte size of a source.
     *
     * @param path source path
     * @return size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long size(final Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the lines in a plain, ZIP, or GZIP source. For ZIP files, the
     * first non-directory entry is used.
     *
     * @param path source path
     * @return lazily read lines; closing the stream closes the source
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        Format format = discover(path);
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> openZip(final Path path) throws IOException {
        ZipInputStream zipStream =
                new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipStream.close();
            throw new IOException(
                    "ZIP source contains no file entries: " + path);
        }
        return lines(zipStream);
    }

    private static Stream<String> openGZip(final Path path) throws IOException {
        return lines(new GZIPInputStream(Files.newInputStream(path)));
    }

    private static Stream<String> lines(final java.io.InputStream input) {
        return new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input))).lines();
    }

    /** Supported GC log source formats. */
    public enum Format {
        /** An uncompressed file. */
        PLAIN_TEXT,
        /** A ZIP archive. */
        ZIP,
        /** A GZIP stream. */
        GZIP,
        /** A file-system directory. */
        DIRECTORY
    }
}
