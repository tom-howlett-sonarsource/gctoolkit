// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
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
 * Shared, low-level utilities for GC log sources.
 *
 * <p>Used by both the API and parser modules to detect the on-disk format
 * of a log source (plain text, ZIP, GZIP, or directory), report its byte
 * size, and open the source as a line-oriented {@link Stream}.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    /** First magic byte of a GZIP file. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second magic byte of a GZIP file. */
    public static final int GZIP_MAGIC2 = 0x8B;
    /** First magic byte of a ZIP file. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second magic byte of a ZIP file. */
    public static final int ZIP_MAGIC2 = 0x4B;

    private LogSources() {
    }

    /**
     * Determine the format of the file at {@code path} by peeking at its
     * magic bytes (or by asking the filesystem whether it is a directory).
     *
     * @param path the path to inspect
     * @return the detected format; {@link LogFileFormat#PLAINTEXT} if the
     *         file exists but is neither GZIP nor ZIP.
     */
    public static LogFileFormat detectFormat(Path path) {
        if (path == null)
            return LogFileFormat.UNKNOWN;
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * @return {@code true} if the first two bytes of the file at
     * {@code path} equal {@code field1} then {@code field2}.
     */
    public static boolean matchesMagic(Path path, int field1, int field2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == field1 && b2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return false;
        }
    }

    /**
     * Return the size in bytes of the file at {@code path}, or {@code -1}
     * if the size cannot be determined.
     */
    public static long byteSize(Path path) {
        if (path == null)
            return -1L;
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to determine byte size for " + path, ioe);
            return -1L;
        }
    }

    /**
     * Open the file at {@code path} as a stream of lines, auto-detecting
     * whether the source is plain text, ZIP, or GZIP.
     *
     * @throws IOException if the file cannot be opened, or has a format that
     *         cannot be streamed as text (for example, a directory).
     */
    public static Stream<String> open(Path path) throws IOException {
        LogFileFormat format = detectFormat(path);
        switch (format) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path + " (format=" + format + ")");
        }
    }

    /** Open a plain-text file as a line stream. */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry in a ZIP file as a line stream.
     * Matches the historical single-entry behavior used by the API's
     * {@code SingleGCLogFile} and the parser's {@code SafepointLogFile}.
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /** Open a GZIP file as a line stream. */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
