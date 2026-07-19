// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

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
 * Utilities for discovering, sizing, and opening line streams over GC log
 * sources on the local file system. The class is stateless; every method
 * accepts an explicit {@link Path} so callers can share it between
 * {@code gctoolkit-api} and {@code gctoolkit-parser} without owning any
 * per-source object.
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
     * Discover the on-disk format of the source at {@code path} by inspecting
     * its first two bytes (or by testing for a directory).
     *
     * @param path source to inspect
     * @return the detected {@link LogSourceFormat}
     */
    public static LogSourceFormat detectFormat(Path path) {
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
     * Return the size in bytes of the file at {@code path}. Directories and
     * unreadable paths report {@code 0}.
     *
     * @param path source whose size should be reported
     * @return the file size in bytes, or {@code 0} if unavailable
     */
    public static long sizeInBytes(Path path) {
        try {
            if (path.toFile().isDirectory()) {
                return 0L;
            }
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, ioe, () -> "Unable to read size of " + path);
            return 0L;
        }
    }

    /**
     * Open a stream of lines over the plain-text file at {@code path}.
     *
     * @param path plain-text log file
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of lines over the first non-directory entry in the
     * ZIP archive at {@code path}.
     *
     * @param path ZIP archive containing a log
     * @return a stream of lines
     * @throws IOException if the archive cannot be opened
     */
    @SuppressWarnings("resource")
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
            return reader.lines().onClose(closeQuietly(reader));
        } catch (IOException | RuntimeException e) {
            zipStream.close();
            throw e;
        }
    }

    /**
     * Open a stream of lines over the GZip-compressed log at {@code path}.
     *
     * @param path GZip-compressed log file
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    @SuppressWarnings("resource")
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
            return reader.lines().onClose(closeQuietly(reader));
        } catch (RuntimeException e) {
            gzipStream.close();
            throw e;
        }
    }

    private static Runnable closeQuietly(BufferedReader reader) {
        return () -> {
            try {
                reader.close();
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, "Failed to close reader", ioe);
            }
        };
    }

    /**
     * Open a line stream over {@code path}, dispatching by detected format.
     * Directories and unknown formats yield {@code null}.
     *
     * @param path source to open
     * @return a stream of lines, or {@code null} if no reader is available
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> open(Path path) throws IOException {
        LogSourceFormat format = detectFormat(path);
        switch (format) {
            case PLAINTEXT:
                return openPlainText(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                return null;
        }
    }

    private static boolean hasMagic(Path path, int field1, int field2) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }
}
