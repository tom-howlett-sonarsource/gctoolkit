// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
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
 * Opens a line-oriented {@link Stream} of text from a GC log source. Three
 * formats are supported:
 * <ul>
 *   <li>plain text — read via {@link Files#lines(Path)}</li>
 *   <li>ZIP — the first non-directory entry is read</li>
 *   <li>GZIP — the compressed stream is decoded transparently</li>
 * </ul>
 * The returned streams are read as UTF-8 and MUST be closed by the caller.
 */
public final class LogFileStreams {

    private LogFileStreams() {
    }

    /**
     * Opens the given plain-text file for line-oriented reading.
     *
     * @param path the file to read
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlainText(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.lines(path);
    }

    /**
     * Opens the first non-directory entry of the given ZIP archive for
     * line-oriented reading.
     *
     * @param path the ZIP archive
     * @return a stream of lines from the first entry
     * @throws IOException if the archive cannot be opened
     */
    @SuppressWarnings("resource")
    public static Stream<String> openZip(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new BufferedInputStream(zipStream), StandardCharsets.UTF_8));
            return reader.lines().onClose(closeQuietly(reader));
        } catch (IOException | RuntimeException e) {
            closeSilently(zipStream);
            throw e;
        }
    }

    /**
     * Opens the given GZIP file for line-oriented reading.
     *
     * @param path the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be opened or is not valid GZIP
     */
    @SuppressWarnings("resource")
    public static Stream<String> openGZip(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new BufferedInputStream(gzipStream), StandardCharsets.UTF_8));
            return reader.lines().onClose(closeQuietly(reader));
        } catch (RuntimeException e) {
            closeSilently(gzipStream);
            throw e;
        }
    }

    private static Runnable closeQuietly(BufferedReader reader) {
        return () -> closeSilently(reader);
    }

    private static void closeSilently(java.io.Closeable resource) {
        try {
            resource.close();
        } catch (IOException ignore) {
            // The caller is closing the stream; propagating another exception would mask the primary failure.
        }
    }
}
