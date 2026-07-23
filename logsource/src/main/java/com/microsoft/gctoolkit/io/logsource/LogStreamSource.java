// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
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
 * Shared utilities for opening GC log sources as line streams,
 * detecting file formats via magic bytes, and computing byte sizes.
 */
public final class LogStreamSource {

    private static final Logger LOGGER = Logger.getLogger(LogStreamSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private LogStreamSource() {
        // utility class
    }

    /**
     * Detect the format of the file at the given path by inspecting
     * magic bytes or checking if it is a directory.
     *
     * @param path the file to inspect
     * @return the detected format
     */
    public static LogSourceFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return LogSourceFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogSourceFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogSourceFormat.ZIP;
        }
        return LogSourceFormat.PLAIN_TEXT;
    }

    /**
     * Open a line stream for the given path, dispatching on the supplied format.
     * For ZIP files, streams the first non-directory entry.
     *
     * @param path   the log file path
     * @param format the pre-detected format
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> lines(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return linesFromZip(path);
            case GZIP:
                return linesFromGZip(path);
            default:
                throw new IOException("Unable to stream lines from " + path);
        }
    }

    /**
     * Open a line stream for the given path, auto-detecting the format.
     *
     * @param path the log file path
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, detectFormat(path));
    }

    /**
     * Return the size of the file in bytes.
     *
     * @param path the file path
     * @return size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a line stream from the first non-directory entry in a ZIP file.
     * The caller should close the returned stream to release the underlying ZIP resources.
     *
     * @param path the ZIP file path
     * @return a stream of lines from the first entry
     * @throws IOException if the ZIP cannot be read or contains no file entries
     */
    @SuppressWarnings("java:S2095") // Resources are closed via Stream.onClose()
    public static Stream<String> linesFromZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipStream.close();
            throw new IOException("ZIP file contains no file entries: " + path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Open a line stream from a GZIP-compressed file.
     * The caller should close the returned stream to release the underlying GZIP resources.
     *
     * @param path the GZIP file path
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("java:S2095") // Resources are closed via Stream.onClose()
    public static Stream<String> linesFromGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int byte1 = in.read();
            int byte2 = in.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException e) {
            return false;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close stream resource", e);
        }
    }
}
