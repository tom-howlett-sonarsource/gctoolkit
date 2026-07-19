// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.log.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared utilities for discovering the format of a GC log source, reporting its
 * size in bytes on disk, and opening a line-oriented {@link Stream} over its
 * contents for plain text, ZIP, and GZIP inputs.
 */
public final class LogFileSources {

    private static final Logger LOG = Logger.getLogger(LogFileSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private LogFileSources() {
        // no instances
    }

    /**
     * Discover the {@link LogFileFormat} of the source at {@code path} by
     * inspecting its magic bytes. Directories are reported as
     * {@link LogFileFormat#DIRECTORY}. If the file cannot be read, the format
     * is reported as {@link LogFileFormat#UNKNOWN}.
     *
     * @param path the source to inspect.
     * @return the discovered {@link LogFileFormat}.
     */
    public static LogFileFormat detectFormat(Path path) {
        if (path == null) {
            return LogFileFormat.UNKNOWN;
        }
        if (Files.isDirectory(path)) {
            return LogFileFormat.DIRECTORY;
        }
        if (!Files.exists(path)) {
            return LogFileFormat.UNKNOWN;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogFileFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogFileFormat.ZIP;
        }
        return LogFileFormat.PLAIN_TEXT;
    }

    /**
     * Read the first two bytes of {@code path} and compare them with the
     * supplied {@code field1} and {@code field2}. Reading errors are logged
     * and reported as no-match.
     *
     * @param path the source to inspect.
     * @param field1 the expected first byte.
     * @param field2 the expected second byte.
     * @return {@code true} if the first two bytes match.
     */
    public static boolean matchesMagic(Path path, int field1, int field2) {
        try (InputStream in = Files.newInputStream(path)) {
            int magicByte1 = in.read();
            int magicByte2 = in.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Return the size in bytes of the regular file at {@code path}.
     *
     * @param path the source to size.
     * @return the size of the file, in bytes.
     * @throws IOException if the size cannot be read.
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a line stream over the source at {@code path}, selecting the
     * appropriate decoder from the discovered {@link LogFileFormat}.
     *
     * @param path the source to open.
     * @return a stream of lines from the source.
     * @throws IOException if the source cannot be opened, or the format is not
     *                     one of plain text, ZIP or GZIP.
     */
    public static Stream<String> openLines(Path path) throws IOException {
        return openLines(path, detectFormat(path));
    }

    /**
     * Open a line stream over the source at {@code path} using the supplied
     * {@code format}.
     *
     * @param path the source to open.
     * @param format the format of the source.
     * @return a stream of lines from the source.
     * @throws IOException if the source cannot be opened, or the format is not
     *                     one of plain text, ZIP or GZIP.
     */
    public static Stream<String> openLines(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return openPlainStream(path);
            case ZIP:
                return openZipStream(path);
            case GZIP:
                return openGZipStream(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a line stream over the plain-text source at {@code path}.
     *
     * @param path the source to open.
     * @return a stream of lines from the source.
     * @throws IOException if the source cannot be opened.
     */
    public static Stream<String> openPlainStream(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over the first non-directory entry of the ZIP source
     * at {@code path}.
     *
     * @param path the source to open.
     * @return a stream of lines from the first non-directory entry.
     * @throws IOException if the source cannot be opened.
     */
    public static Stream<String> openZipStream(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a line stream over the GZIP source at {@code path}.
     *
     * @param path the source to open.
     * @return a stream of lines from the source.
     * @throws IOException if the source cannot be opened.
     */
    public static Stream<String> openGZipStream(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

}
