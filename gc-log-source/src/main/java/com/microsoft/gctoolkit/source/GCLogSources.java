// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

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
 * Shared IO utilities for GC log sources. Centralises the plain / ZIP / GZIP
 * stream-open logic and the file-system helpers (byte size, directory listing)
 * used by both the api and parser modules.
 */
public final class GCLogSources {

    private GCLogSources() {
    }

    /**
     * Open a plain-text log file as a stream of lines.
     *
     * @param path the log file path
     * @return a stream of lines from the file
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> openPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-compressed log file as a stream of lines from its first
     * non-directory entry.
     *
     * @param path the ZIP file path
     * @return a stream of lines from the first non-directory entry, or an
     *         empty stream if the archive contains no such entry
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipStream.close();
            return Stream.empty();
        }
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP-compressed log file as a stream of lines.
     *
     * @param path the GZIP file path
     * @return a stream of lines from the decompressed content
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Return the size of the source file, in bytes.
     *
     * @param path the log file path
     * @return the size of the file in bytes
     * @throws IOException if the file's attributes cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * List the immediate contents of a directory as a stream of paths for
     * source discovery. The caller is responsible for closing the stream.
     *
     * @param directory a directory path
     * @return a stream of entries in the directory
     * @throws IOException if the directory cannot be listed
     */
    public static Stream<Path> listSources(Path directory) throws IOException {
        return Files.list(directory);
    }
}
