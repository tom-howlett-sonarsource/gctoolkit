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
 * Opens line streams over GC log sources. Handles plain-text files, single-entry
 * ZIP archives, and GZIP files. Consolidates stream-opening code previously
 * duplicated across the API and parser modules.
 */
public final class GCLogStreams {

    private GCLogStreams() {
    }

    /**
     * Open a plain-text GC log file as a stream of lines.
     * @param path The path to a UTF-8 text log file.
     * @return A stream of lines.
     * @throws IOException on I/O failure.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry of a ZIP archive as a stream of lines.
     * @param path The path to the ZIP archive.
     * @return A stream of lines drawn from the first log entry.
     * @throws IOException on I/O failure.
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
     * Open a GZIP-compressed GC log file as a stream of lines.
     * @param path The path to the GZIP-compressed log file.
     * @return A stream of lines.
     * @throws IOException on I/O failure.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
