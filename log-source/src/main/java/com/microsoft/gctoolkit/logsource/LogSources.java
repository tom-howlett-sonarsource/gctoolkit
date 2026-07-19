// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared utilities for discovering the format of a GC log source, measuring
 * its size in bytes, and opening a {@link Stream} of lines over plain-text,
 * ZIP, and GZIP log files. The stream methods deliberately mirror the
 * behaviour used historically by the API and parser modules so callers can be
 * switched over without any user-visible change.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private LogSources() {
        // Utility class.
    }

    /**
     * Discover the on-disk format of the given path. A directory is reported
     * as {@link LogSourceFormat#DIRECTORY}; regular files are classified by
     * their leading magic bytes.
     *
     * @param path the path to examine, must not be {@code null}
     * @return the detected format, or {@link LogSourceFormat#UNKNOWN} if the
     *         path cannot be read
     */
    public static LogSourceFormat detectFormat(Path path) {
        if (path == null) {
            return LogSourceFormat.UNKNOWN;
        }
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogSourceFormat.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogSourceFormat.ZIP;
        }
        if (Files.isRegularFile(path)) {
            return LogSourceFormat.PLAINTEXT;
        }
        return LogSourceFormat.UNKNOWN;
    }

    /**
     * Check whether the first two bytes of the file match the given values.
     *
     * @param path the file to probe, must not be {@code null}
     * @param field1 the expected first byte
     * @param field2 the expected second byte
     * @return {@code true} when both bytes match; {@code false} on any I/O
     *         failure or mismatch
     */
    public static boolean hasMagic(Path path, int field1, int field2) {
        try (InputStream in = Files.newInputStream(path)) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == field1 && b2 == field2;
        } catch (IOException ioe) {
            LOG.log(Level.FINE, ioe, () -> "Unable to read magic bytes from " + path);
        }
        return false;
    }

    /**
     * Return the size in bytes of a regular file, or {@code -1} when the path
     * is not accessible.
     *
     * @param path the file whose size is requested, must not be {@code null}
     * @return the size in bytes, or {@code -1} on failure
     */
    public static long byteSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.log(Level.FINE, ioe, () -> "Unable to determine size of " + path);
            return -1L;
        }
    }

    /**
     * Open a stream of lines over a plain text log file.
     *
     * @param path the log file, must not be {@code null}
     * @return a stream of lines; the caller is responsible for closing it
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlainLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of lines over a ZIP-compressed log file. Only the first
     * non-directory entry is consumed. This matches the behaviour of the
     * previous per-module implementations.
     *
     * @param path the ZIP file, must not be {@code null}
     * @return a stream of lines; the caller is responsible for closing it
     * @throws IOException if the file cannot be opened
     */
    @SuppressWarnings("resource")
    public static Stream<String> openZipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a stream of lines over a GZIP-compressed log file.
     *
     * @param path the GZIP file, must not be {@code null}
     * @return a stream of lines; the caller is responsible for closing it
     * @throws IOException if the file cannot be opened
     */
    @SuppressWarnings("resource")
    public static Stream<String> openGZipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
