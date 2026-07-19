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
 * Opens line-oriented streams over GC log sources stored as plain text, single-entry ZIP
 * archives, or GZIP archives. Callers are responsible for closing the returned streams.
 */
public final class LogFileStreams {

    private LogFileStreams() {
    }

    /**
     * Open a plain-text log file as a stream of lines.
     * @param path The file to open.
     * @return A stream of the file's lines.
     * @throws IOException If the file cannot be opened.
     */
    public static Stream<String> openPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry of a ZIP archive as a stream of lines.
     * @param path The archive to open.
     * @return A stream of the entry's lines.
     * @throws IOException If the archive cannot be opened.
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
     * Open a GZIP archive as a stream of lines.
     * @param path The archive to open.
     * @return A stream of the archive's lines.
     * @throws IOException If the archive cannot be opened.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
