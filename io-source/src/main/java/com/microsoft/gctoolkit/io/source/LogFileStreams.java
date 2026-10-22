// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
 * Opens a GC log source as a stream of lines. Plain, ZIP (first non-directory
 * entry), and GZIP sources are supported. The returned streams are unfiltered;
 * callers are responsible for any trimming, filtering, or end-of-data handling.
 */
public final class LogFileStreams {

    private LogFileStreams() {
    }

    /**
     * Open a plain text source as a stream of lines.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public static Stream<String> plain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP source as a stream of lines, reading the first non-directory
     * entry.
     * @param path The path to the source.
     * @return A stream of the lines in the first entry of the archive.
     * @throws IOException If the source cannot be read.
     */
    public static Stream<String> zip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP source as a stream of lines.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public static Stream<String> gzip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Open the source at the given path as a stream of lines, discovering the
     * format from its leading bytes.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException If the format cannot be read as a log stream.
     */
    public static Stream<String> open(Path path) throws IOException {
        switch (LogFileFormat.discover(path)) {
            case PLAINTEXT:
                return plain(path);
            case ZIP:
                return zip(path);
            case GZIP:
                return gzip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }
}
