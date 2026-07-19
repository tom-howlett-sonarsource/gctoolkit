// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

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
 * Shared openers for the three log stream formats used across the API and
 * parser modules: plain text, ZIP, and GZIP. Each method returns a
 * {@link Stream} of lines from the underlying source.
 */
public final class LogStreams {

    private LogStreams() {
    }

    /**
     * Open the given plain-text log file and stream its lines.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the given ZIP file and stream lines from its first non-directory
     * entry. The stream owns the underlying ZIP input stream and closes it
     * when consumed.
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
     * Open the given GZIP file and stream its lines.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Dispatch to the appropriate opener for the given {@code format}.
     * Directory or unknown formats yield an {@link IOException}.
     */
    public static Stream<String> open(Path path, LogStreamFormat format) throws IOException {
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
