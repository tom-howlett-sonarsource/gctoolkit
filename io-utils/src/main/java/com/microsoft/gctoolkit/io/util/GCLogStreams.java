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
 * Factory methods for opening a line-oriented {@link Stream} of a GC log source.
 * Callers who already know the format of the source can use one of the
 * format-specific factories; the {@link #open(Path, GCLogSourceFormat)} entry
 * point dispatches based on a previously detected {@link GCLogSourceFormat}.
 */
public final class GCLogStreams {

    private GCLogStreams() {
    }

    /**
     * Open a plain text log file as a stream of lines.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry of a ZIP file as a stream of lines.
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
     * Open a GZIP compressed log file as a stream of lines.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Open a log source using the supplied format.
     *
     * @param path the file to read
     * @param format the previously detected source format
     * @return a stream of the lines of the log file
     * @throws IOException if the file cannot be opened, or if {@code format}
     *     does not identify a readable stream format
     */
    public static Stream<String> open(Path path, GCLogSourceFormat format) throws IOException {
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
