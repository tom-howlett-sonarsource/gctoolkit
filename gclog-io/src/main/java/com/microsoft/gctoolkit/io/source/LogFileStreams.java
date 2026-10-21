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
 * Shared utilities for opening a GC log source as a {@link Stream} of lines.
 * <p>
 * The plain text, ZIP and GZIP stream opening logic was previously duplicated across the
 * {@code api} and {@code parser} modules. It is collected here so that both modules share
 * a single implementation.
 */
public final class LogFileStreams {

    private LogFileStreams() {
    }

    /**
     * Open a plain text source as a stream of lines.
     * @param path the path to the source.
     * @return a stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> plainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP source as a stream of lines, reading from the first non-directory entry.
     * @param path the path to the source.
     * @return a stream of the lines in the first file entry of the ZIP source.
     * @throws IOException if the source cannot be read.
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
     * @param path the path to the source.
     * @return a stream of the lines in the GZIP source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> gzip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
