// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
 * Shared, module-agnostic utilities for GC log source discovery, byte sizing
 * and opening plain, ZIP and GZIP log streams. Consumed by both the API
 * ({@code com.microsoft.gctoolkit.io}) and parser
 * ({@code com.microsoft.gctoolkit.parser.io}) modules so that log-source
 * handling lives in a single place.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSources() {
    }

    /**
     * Detect the on-disk format of {@code path} using directory-vs-file checks
     * and by inspecting the first two bytes for the GZIP and ZIP magic values.
     *
     * @param path the log source path
     * @return the detected {@link GCLogSourceFormat}; {@link GCLogSourceFormat#UNKNOWN}
     *         when {@code path} is {@code null} or does not exist
     */
    public static GCLogSourceFormat detect(Path path) {
        if (path == null || !Files.exists(path)) {
            return GCLogSourceFormat.UNKNOWN;
        }
        if (Files.isDirectory(path)) {
            return GCLogSourceFormat.DIRECTORY;
        }
        int[] head = readFirstTwoBytes(path);
        if (head == null) {
            return GCLogSourceFormat.UNKNOWN;
        }
        if (head[0] == GZIP_MAGIC1 && head[1] == GZIP_MAGIC2) {
            return GCLogSourceFormat.GZIP;
        }
        if (head[0] == ZIP_MAGIC1 && head[1] == ZIP_MAGIC2) {
            return GCLogSourceFormat.ZIP;
        }
        return GCLogSourceFormat.PLAINTEXT;
    }

    /**
     * Return the size in bytes of {@code path}, or {@code -1} when it cannot
     * be determined (missing file, directory, I/O error).
     *
     * @param path the log source path
     * @return the file size in bytes, or {@code -1} if it cannot be read
     */
    public static long sizeInBytes(Path path) {
        if (path == null) {
            return -1L;
        }
        try {
            if (Files.isRegularFile(path)) {
                return Files.size(path);
            }
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to determine size of " + path, ioe);
        }
        return -1L;
    }

    /**
     * Open a line stream over {@code path} according to {@code format}.
     * <p>
     * For {@link GCLogSourceFormat#ZIP} archives, the first non-directory
     * entry is streamed. This mirrors the behavior previously duplicated in
     * {@code SingleGCLogFile} and {@code SafepointLogFile}.
     *
     * @param path   the log source path
     * @param format the format previously returned by {@link #detect(Path)}
     * @return a stream of lines from the log source
     * @throws IOException if the source cannot be opened, or the format is
     *                     not a streamable single-file format
     */
    public static Stream<String> openStream(Path path, GCLogSourceFormat format) throws IOException {
        if (format == null) {
            throw new IOException("Unable to read " + path + ": unknown format");
        }
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return openZipFirstEntry(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path + ": unsupported format " + format);
        }
    }

    /**
     * Open a line stream over the first non-directory entry of the ZIP
     * archive at {@code path}.
     *
     * @param path the ZIP archive path
     * @return a stream of lines from the first entry
     * @throws IOException if the archive cannot be opened or contains no entries
     */
    public static Stream<String> openZipFirstEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry = zipStream.getNextEntry();
        while (entry != null && entry.isDirectory()) {
            entry = zipStream.getNextEntry();
        }
        if (entry == null) {
            zipStream.close();
            throw new IOException("No file entries found in ZIP archive: " + path);
        }
        return bufferedLines(zipStream);
    }

    /**
     * Open a line stream over the GZIP-compressed file at {@code path}.
     *
     * @param path the GZIP file path
     * @return a stream of lines from the decompressed content
     * @throws IOException if the archive cannot be opened
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return bufferedLines(gzipStream);
    }

    private static Stream<String> bufferedLines(InputStream in) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(in), StandardCharsets.UTF_8)).lines();
    }

    private static int[] readFirstTwoBytes(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            int b1 = in.read();
            int b2 = in.read();
            if (b1 < 0 || b2 < 0) {
                return null;
            }
            return new int[]{b1, b2};
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return null;
        }
    }
}
