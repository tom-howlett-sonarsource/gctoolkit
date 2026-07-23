// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
 * Shared utilities for opening GC log source files as line streams.
 * <p>
 * Supports plain-text, ZIP-compressed, and GZIP-compressed log files.
 * The {@link #lines(Path)} method auto-detects the format via {@link FormatDetector}.
 */
public final class LogSourceStreams {

    private LogSourceStreams() {}

    /**
     * Open the file at the given path as a {@code Stream<String>}, auto-detecting
     * whether it is plain text, ZIP, or GZIP.
     *
     * @param path path to the log file
     * @return a stream of lines from the file
     * @throws IOException if the file cannot be read or has an unsupported format
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, FormatDetector.detect(path));
    }

    /**
     * Open the file at the given path as a {@code Stream<String>} using the
     * specified format.
     *
     * @param path   path to the log file
     * @param format the file format
     * @return a stream of lines from the file
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> lines(Path path, FileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return plainTextLines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unsupported file format for streaming: " + format);
        }
    }

    /**
     * Open a plain-text file as a stream of lines.
     *
     * @param path path to the plain-text file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP file and stream lines from the first non-directory entry.
     *
     * @param path path to the ZIP file
     * @return a stream of lines from the first entry
     * @throws IOException if the file cannot be read or contains no entries
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP file and stream its lines.
     *
     * @param path path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Return the byte size of the file at the given path.
     *
     * @param path path to the file
     * @return the file size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }
}
