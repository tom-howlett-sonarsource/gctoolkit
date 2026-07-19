// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

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
 * Utilities for opening GC log line streams from plain, ZIP and GZIP
 * files on disk. Callers are responsible for closing the returned
 * streams — {@link Stream#close()} propagates to the underlying
 * {@link BufferedReader} and thereby to the input stream chain.
 */
public final class GCLogStreams {

    private GCLogStreams() {
        // utility
    }

    /**
     * Open a plain-text log file and return its lines.
     *
     * @param path the plain-text file
     * @return the lines of {@code path}
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> plainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP archive at {@code path}, skip any leading directory
     * entries, and return the lines of the first regular entry.
     *
     * @param path the ZIP archive
     * @return the lines of the first non-directory entry
     * @throws IOException if the archive cannot be opened
     */
    public static Stream<String> zipFirstEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP-compressed log file and return its lines.
     *
     * @param path the GZIP file
     * @return the lines of the decompressed content
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> gzip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
