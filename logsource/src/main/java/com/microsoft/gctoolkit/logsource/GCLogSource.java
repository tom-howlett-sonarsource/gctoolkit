// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
 * Shared utilities for GC log source discovery, byte sizing, and opening
 * plain-text, ZIP, and GZIP log streams.
 * <p>
 * This class consolidates I/O behavior that was previously duplicated across
 * the API and parser modules.
 * <p>
 * The stream-returning methods follow the same ownership convention as
 * {@link Files#lines(Path)}: the caller must close the returned
 * {@code Stream} (e.g. via try-with-resources) to release the underlying
 * I/O resources. Each returned stream has an {@code onClose} handler
 * registered to clean up its backing reader.
 */
public final class GCLogSource {

    private static final Logger LOGGER = Logger.getLogger(GCLogSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSource() {
        // utility class
    }

    /**
     * Detect the file format of the given path by inspecting magic bytes.
     *
     * @param path the path to inspect
     * @return the detected {@link FileFormat}
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        } else if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        } else if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        } else {
            return FileFormat.PLAINTEXT;
        }
    }

    /**
     * Open a line stream over a plain-text file.
     *
     * @param path the file to read
     * @return a stream of lines; the caller must close the stream
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over the first non-directory entry in a ZIP file.
     * The caller must close the returned stream to release the underlying
     * ZIP input stream.
     *
     * @param path the ZIP file to read
     * @return a stream of lines from the first entry
     * @throws IOException if the file cannot be read or contains no entries
     */
    @SuppressWarnings("java:S2095") // Resources are closed via the stream's onClose handler
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipStream.close();
            throw new IOException("ZIP file contains no entries: " + path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Open a line stream over a GZIP-compressed file.
     * The caller must close the returned stream to release the underlying
     * GZIP input stream.
     *
     * @param path the GZIP file to read
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("java:S2095") // Resources are closed via the stream's onClose handler
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Open a line stream over the given path, auto-detecting the format.
     *
     * @param path the file to read
     * @return a stream of lines; the caller must close the stream
     * @throws IOException if the format is unsupported or the file cannot be read
     */
    public static Stream<String> stream(Path path) throws IOException {
        FileFormat format = detectFormat(path);
        switch (format) {
            case PLAINTEXT:
                return streamPlainText(path);
            case ZIP:
                return streamZip(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to stream file: " + path);
        }
    }

    /**
     * Return the byte size of the file at the given path.
     *
     * @param path the file to measure
     * @return the size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long byteCount(Path path) throws IOException {
        return Files.size(path);
    }

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int byte1 = in.read();
            int byte2 = in.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return false;
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close resource", e);
        }
    }
}
