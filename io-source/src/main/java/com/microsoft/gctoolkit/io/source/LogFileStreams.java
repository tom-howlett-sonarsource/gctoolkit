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
 * Opens GC log sources as a {@link Stream} of lines. A single utility shared by the API and
 * parser modules so the plain, ZIP, and GZIP reading behavior lives in one place.
 */
public final class LogFileStreams {

    private LogFileStreams() {
    }

    /**
     * Open a plain text log file.
     * @param path The path to the log file.
     * @return A stream of the lines in the file.
     * @throws IOException Thrown when the file cannot be read.
     */
    public static Stream<String> plainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first (non-directory) entry of a ZIP archive.
     * @param path The path to the ZIP archive.
     * @return A stream of the lines in the first entry.
     * @throws IOException Thrown when the archive cannot be read.
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
     * Open a GZip compressed log file.
     * @param path The path to the GZip file.
     * @return A stream of the lines in the file.
     * @throws IOException Thrown when the file cannot be read.
     */
    public static Stream<String> gzip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
