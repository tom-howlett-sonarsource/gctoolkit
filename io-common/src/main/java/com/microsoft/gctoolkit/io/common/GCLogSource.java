// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.common;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
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
 * Utility for GC log source discovery and stream opening. Handles the three
 * on-disk encodings the toolkit reads: plain text, ZIP archives, and GZIP
 * compressed logs.
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSource() {
    }

    /**
     * Discover the on-disk format of the given path by inspecting magic bytes
     * (or, if the path is a directory, returning {@link SourceFormat#DIRECTORY}).
     *
     * @param path path to probe.
     * @return the detected {@link SourceFormat}; {@link SourceFormat#UNKNOWN} when the file cannot be read.
     */
    public static SourceFormat detectFormat(Path path) {
        if (path == null) {
            return SourceFormat.UNKNOWN;
        }
        if (Files.isDirectory(path)) {
            return SourceFormat.DIRECTORY;
        }
        if (!Files.isRegularFile(path)) {
            return SourceFormat.UNKNOWN;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return SourceFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return SourceFormat.ZIP;
        }
        return SourceFormat.PLAINTEXT;
    }

    /**
     * Report the size in bytes of the file at {@code path}, or {@code -1} when the
     * size cannot be determined (missing file, permission error, etc.).
     *
     * @param path path to query.
     * @return the size in bytes, or {@code -1} on failure.
     */
    public static long sizeInBytes(Path path) {
        if (path == null) {
            return -1L;
        }
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to determine size of " + path, ioe);
            return -1L;
        }
    }

    /**
     * Open a plain-text log file as a line stream. The caller is responsible for
     * closing the returned stream.
     *
     * @param path path to a plain-text log.
     * @return a stream of lines.
     * @throws IOException when the file cannot be opened.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-encoded log file as a line stream backed by its first non-directory
     * entry. The caller is responsible for closing the returned stream.
     *
     * @param path path to a ZIP archive containing at least one log entry.
     * @return a stream of lines from the first non-directory entry.
     * @throws IOException when the archive cannot be opened.
     */
    @SuppressWarnings("resource")
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return linesFrom(zipStream);
        } catch (IOException | RuntimeException failure) {
            closeQuietly(zipStream);
            throw failure;
        }
    }

    /**
     * Open a GZIP compressed log file as a line stream. The caller is responsible
     * for closing the returned stream.
     *
     * @param path path to a GZIP compressed log.
     * @return a stream of lines.
     * @throws IOException when the file cannot be opened.
     */
    @SuppressWarnings("resource")
    public static Stream<String> openGZip(Path path) throws IOException {
        InputStream fileStream = Files.newInputStream(path);
        try {
            GZIPInputStream gzipStream = new GZIPInputStream(fileStream);
            return linesFrom(gzipStream);
        } catch (IOException | RuntimeException failure) {
            closeQuietly(fileStream);
            throw failure;
        }
    }

    private static Stream<String> linesFrom(InputStream in) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(in), StandardCharsets.UTF_8)).lines();
    }

    private static boolean matchesMagic(Path path, int firstByte, int secondByte) {
        try (InputStream in = Files.newInputStream(path)) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == firstByte && b2 == secondByte;
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to read magic bytes from " + path, ioe);
            return false;
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignore) {
            // best effort during exception unwind
        }
    }
}
