// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

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
 * Opens plain-text, ZIP, and GZIP GC log files as a {@link Stream} of lines.
 * <p>
 * For ZIP files the first non-directory entry is used.
 */
public final class LogFileStreams {

    private LogFileStreams() {
        // static helpers only
    }

    /**
     * Opens a plain-text file as a stream of lines.
     *
     * @param path path to the file.
     * @return a stream of the file's lines.
     * @throws IOException if the file cannot be read.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Opens a ZIP file and returns a stream over the first non-directory entry.
     *
     * @param path path to the ZIP file.
     * @return a stream of the ZIP entry's lines.
     * @throws IOException if the file cannot be opened.
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Opens a GZIP file as a stream of lines.
     *
     * @param path path to the GZIP file.
     * @return a stream of the file's lines.
     * @throws IOException if the file cannot be opened.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Opens the given file using the appropriate underlying stream for its
     * detected {@link LogFileFormat}.
     *
     * @param path   path to the file.
     * @param format the detected format.
     * @return a stream of the file's lines.
     * @throws IOException if the file cannot be opened, or if the format is not
     *                     a supported source (i.e. not PLAINTEXT, ZIP, or GZIP).
     */
    public static Stream<String> open(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }
}
