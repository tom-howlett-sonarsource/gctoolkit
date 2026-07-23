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
 * Utility for opening GC log files as line streams regardless of compression format.
 * <p>
 * Callers must close the returned streams to release underlying resources.
 */
public final class GCLogStreams {

    private GCLogStreams() {
    }

    /**
     * Open a plain-text file as a stream of lines.
     *
     * @param path the path to the plain-text file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry in a ZIP file as a stream of lines.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from the first entry
     * @throws IOException if the file cannot be read or contains no entries
     */
    @SuppressWarnings("resource") // Resources are closed via the returned stream's onClose handler
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipStream.close();
            throw new IOException("ZIP file contains no entries: " + path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ignored) {
                // closing best-effort
            }
        });
    }

    /**
     * Open a GZIP-compressed file as a stream of lines.
     *
     * @param path the path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("resource") // Resources are closed via the returned stream's onClose handler
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ignored) {
                // closing best-effort
            }
        });
    }

    /**
     * Open a GC log file as a stream of lines, detecting the format automatically.
     *
     * @param path the path to the log file
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> open(Path path) throws IOException {
        FileFormat format = FileFormatDetector.detect(path);
        return open(path, format);
    }

    /**
     * Open a GC log file as a stream of lines using the given format.
     *
     * @param path   the path to the log file
     * @param format the file format
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> open(Path path, FileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }
}
