// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared utilities for detecting log source format and opening
 * plain-text, ZIP, and GZIP log streams.
 */
public final class LogSourceStreams {

    private static final Logger LOG = Logger.getLogger(LogSourceStreams.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private LogSourceStreams() {
    }

    /**
     * Detect the format of the file at the given path by examining magic bytes.
     * @param path The path to the file.
     * @return The detected {@link LogSourceFormat}.
     */
    public static LogSourceFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return LogSourceFormat.DIRECTORY;
        }
        if (magicMatches(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogSourceFormat.GZIP;
        }
        if (magicMatches(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogSourceFormat.ZIP;
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * Open a stream of lines from the given path, auto-detecting the format.
     * @param path The path to the log file.
     * @return A stream of lines.
     * @throws IOException if the file cannot be read or the format is unsupported.
     */
    public static Stream<String> stream(Path path) throws IOException {
        return stream(path, detectFormat(path));
    }

    /**
     * Open a stream of lines from the given path using the specified format.
     * @param path The path to the log file.
     * @param format The known format of the file.
     * @return A stream of lines.
     * @throws IOException if the file cannot be read or the format is unsupported.
     */
    public static Stream<String> stream(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return streamPlain(path);
            case ZIP:
                return streamZip(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to stream " + path + " with format " + format);
        }
    }

    /**
     * Open a plain-text file as a stream of lines.
     * @param path The path to the plain-text file.
     * @return A stream of lines.
     * @throws IOException if the file cannot be read.
     */
    public static Stream<String> streamPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry in a ZIP file as a stream of lines.
     * @param path The path to the ZIP file.
     * @return A stream of lines from the first non-directory ZIP entry.
     * @throws IOException if the file cannot be read.
     */
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP file as a stream of lines.
     * @param path The path to the GZIP file.
     * @return A stream of lines.
     * @throws IOException if the file cannot be read.
     */
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Return the size in bytes of the file at the given path.
     * @param path The path to the file.
     * @return The file size in bytes.
     * @throws IOException if the size cannot be determined.
     */
    public static long fileSize(Path path) throws IOException {
        return Files.size(path);
    }

    private static boolean magicMatches(Path path, int expected1, int expected2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int byte1 = in.read();
            int byte2 = in.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException e) {
            LOG.warning(e.getMessage());
        }
        return false;
    }
}
