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
 * Shared source-discovery, byte sizing, and stream-opening utilities for GC log
 * files. Centralises the previously duplicated production IO behavior in the
 * API and parser modules.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private LogSources() {
    }

    /**
     * Discover the format of the file at the given path by inspecting the file
     * system entry (directory vs. file) and, for regular files, reading the
     * leading magic bytes.
     *
     * @param path the path to inspect
     * @return the discovered format, or {@link LogSourceFormat#UNKNOWN} if the
     *         path does not exist or cannot be read
     */
    public static LogSourceFormat detectFormat(Path path) {
        if (path == null)
            return LogSourceFormat.UNKNOWN;
        if (path.toFile().isDirectory())
            return LogSourceFormat.DIRECTORY;
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogSourceFormat.GZIP;
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogSourceFormat.ZIP;
        if (path.toFile().isFile())
            return LogSourceFormat.PLAIN_TEXT;
        return LogSourceFormat.UNKNOWN;
    }

    /**
     * Test whether the first two bytes of the file match the supplied values.
     *
     * @param path the file to inspect
     * @param first the expected value of the first byte
     * @param second the expected value of the second byte
     * @return {@code true} if the leading bytes match, {@code false} otherwise
     *         or if the file cannot be read
     */
    public static boolean matchesMagic(Path path, int first, int second) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == first && magicByte2 == second;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Return the size, in bytes, of the file at the given path.
     *
     * @param path the file to size
     * @return size in bytes
     * @throws IOException if the file cannot be sized
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a line stream over a file whose format has already been discovered.
     *
     * @param path the file to open
     * @param format the file's format
     * @return a lazy line stream
     * @throws IOException if the file cannot be opened, or the format is not
     *         readable as a line stream (e.g. {@link LogSourceFormat#DIRECTORY})
     */
    public static Stream<String> openLines(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return openPlainLines(path);
            case ZIP:
                return openZipLines(path);
            case GZIP:
                return openGZipLines(path);
            default:
                throw new IOException("Unable to read " + path + " (format=" + format + ")");
        }
    }

    /**
     * Open a plain-text file as a line stream.
     *
     * @param path the file to open
     * @return a lazy line stream
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlainLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-compressed file as a line stream. The stream reads from the
     * first non-directory entry in the archive.
     *
     * @param path the file to open
     * @return a lazy line stream
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openZipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Open a GZIP-compressed file as a line stream.
     *
     * @param path the file to open
     * @return a lazy line stream
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openGZipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
    }
}
