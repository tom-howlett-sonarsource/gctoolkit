// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared production utilities for GC log source discovery, byte sizing, and
 * opening plain, ZIP, and GZIP log streams.
 *
 * <p>Consumed by both the API module ({@code SingleGCLogFile},
 * {@code RotatingGCLogFile}, {@code LogFileMetadata}) and the parser module
 * ({@code SafepointLogFile}). Provides a single implementation for each
 * behavior so callers cannot drift apart.</p>
 */
public final class LogSource {

    private static final Logger LOG = Logger.getLogger(LogSource.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;
    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private LogSource() {
    }

    /**
     * Discover the on-disk format of the given path by inspecting the first
     * two magic bytes for regular files, or reporting {@link LogSourceFormat#DIRECTORY}
     * for directories. Returns {@link LogSourceFormat#PLAINTEXT} when the file
     * exists but neither ZIP nor GZIP magic is present.
     *
     * @param path path to inspect; must not be {@code null}
     * @return the detected {@link LogSourceFormat}
     */
    public static LogSourceFormat detectFormat(Path path) {
        if (path.toFile().isDirectory())
            return LogSourceFormat.DIRECTORY;
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogSourceFormat.GZIP;
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogSourceFormat.ZIP;
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * Return the size, in bytes, of the given regular file.
     *
     * @param path path to a regular file; must not be a directory
     * @return the file size in bytes
     * @throws IOException if the file cannot be inspected
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a plain-text log file for line-by-line streaming.
     *
     * @param path path to a plain-text log file
     * @return a stream of lines; the caller must close it
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-compressed log file and stream the concatenated lines of the
     * first non-directory entry. The returned stream owns the underlying reader
     * and releases it on {@link Stream#close()}.
     *
     * @param path path to a ZIP file
     * @return a stream of lines; the caller must close it
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return streamLinesAndClose(zipStream);
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipStream);
            throw e;
        }
    }

    /**
     * Open a GZIP-compressed log file for line-by-line streaming. The returned
     * stream owns the underlying reader and releases it on
     * {@link Stream#close()}.
     *
     * @param path path to a GZIP file
     * @return a stream of lines; the caller must close it
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        try {
            return streamLinesAndClose(gzipStream);
        } catch (RuntimeException re) {
            closeQuietly(gzipStream);
            throw re;
        }
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamLinesAndClose(InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static boolean hasMagic(Path path, int expected1, int expected2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == expected1 && magicByte2 == expected2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to close log source resource", e);
        }
    }
}
