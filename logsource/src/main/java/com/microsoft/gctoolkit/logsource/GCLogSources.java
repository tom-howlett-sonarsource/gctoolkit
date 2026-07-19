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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared IO helpers for GC log sources: source discovery via magic-byte inspection,
 * byte sizing, and opening plain, ZIP, and GZIP log streams. Intended for reuse across
 * modules so that these low-level file behaviours live in a single place.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    /** First magic byte of a GZIP stream. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second magic byte of a GZIP stream. */
    public static final int GZIP_MAGIC2 = 0x8B;

    /** First magic byte of a ZIP archive. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second magic byte of a ZIP archive. */
    public static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSources() {
    }

    /**
     * Discover the format of the source at {@code path} by inspecting the leading bytes
     * of the file, or by checking whether the path resolves to a directory.
     *
     * @param path the path to inspect
     * @return the discovered format, never {@code null}
     */
    public static LogSourceFormat discover(Path path) {
        if (path == null) {
            return LogSourceFormat.UNKNOWN;
        }
        if (path.toFile().isDirectory()) {
            return LogSourceFormat.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogSourceFormat.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogSourceFormat.ZIP;
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} when the first two bytes of the file match {@code field1} and
     * {@code field2}. Returns {@code false} on any IO problem and logs a warning.
     *
     * @param path the file to inspect
     * @param field1 the expected value of the first byte
     * @param field2 the expected value of the second byte
     * @return {@code true} if the leading bytes match
     */
    public static boolean hasMagic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Return the size in bytes of the file at {@code path}, or {@code -1} if the size cannot be
     * determined (e.g. the path does not exist or is not a regular file). This is a small helper
     * so callers do not need to catch {@link IOException} for a diagnostic look-up.
     *
     * @param path the file to size
     * @return the file size in bytes, or {@code -1} on error
     */
    public static long byteSize(Path path) {
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
     * Open a plain-text log file for streaming, one line at a time.
     *
     * @param path the file to open
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry of a ZIP archive as a stream of lines.
     *
     * @param path the ZIP archive
     * @return a stream of lines from the first entry
     * @throws IOException if the archive cannot be opened
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP-compressed log file as a stream of lines.
     *
     * @param path the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Open a log source of the given {@code format}. Callers that have already discovered the
     * format via {@link #discover(Path)} can dispatch through this method.
     *
     * @param path the source to open
     * @param format the previously discovered format
     * @return a stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }
}
