// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.sources.io;

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
 * Opens a line {@link Stream} over a GC log source, transparently handling
 * plain text, ZIP and GZIP files.
 * <p>
 * The behaviour mirrors the pre-existing IO opened by
 * {@code SingleGCLogFile} in the API module and {@code SafepointLogFile} in the
 * parser module which previously each maintained their own copy of these
 * methods.
 */
public final class LogFileStreams {

    private LogFileStreams() {
        // static utility
    }

    /**
     * Return a line stream over the plain text file at {@code path}.
     *
     * @param path path to the file.
     * @return a stream of lines from the file.
     * @throws IOException when the file cannot be opened.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Return a line stream over the first non-directory entry in the ZIP file
     * at {@code path}.
     * <p>
     * The returned stream owns the underlying {@link ZipInputStream}; callers
     * should close it once fully consumed.
     *
     * @param path path to the ZIP file.
     * @return a stream of lines from the first entry in the archive.
     * @throws IOException when the archive cannot be opened.
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
     * Return a line stream over the GZIP file at {@code path}.
     * <p>
     * The returned stream owns the underlying {@link GZIPInputStream}; callers
     * should close it once fully consumed.
     *
     * @param path path to the GZIP file.
     * @return a stream of lines from the GZIP file.
     * @throws IOException when the file cannot be opened.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
