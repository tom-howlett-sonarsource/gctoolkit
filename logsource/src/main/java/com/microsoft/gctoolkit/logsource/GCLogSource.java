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
 * Shared utilities for GC log source discovery, byte sizing, and
 * opening plain-text, ZIP, and GZIP log streams.
 */
public final class GCLogSource {

    private static final Logger LOGGER = Logger.getLogger(GCLogSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    /**
     * The format of a GC log source file.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN
    }

    private GCLogSource() {}

    /**
     * Detect the format of a log source file by inspecting its magic bytes.
     * Directories are detected via the file system. If the magic bytes cannot
     * be read, {@link Format#PLAINTEXT} is returned.
     *
     * @param path the path to inspect
     * @return the detected format
     */
    public static Format detectFormat(Path path) {
        if (path.toFile().isDirectory()) return Format.DIRECTORY;
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) return Format.GZIP;
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) return Format.ZIP;
        return Format.PLAINTEXT;
    }

    /**
     * Return the size of the file in bytes.
     *
     * @param path the path to the file
     * @return the file size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a line stream for the given path, auto-detecting the format.
     * Supports plain text, ZIP (first non-directory entry), and GZIP files.
     *
     * @param path the path to the log file
     * @return a stream of lines from the file
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> stream(Path path) throws IOException {
        Format format = detectFormat(path);
        return stream(path, format);
    }

    /**
     * Open a line stream for the given path using the specified format.
     *
     * @param path   the path to the log file
     * @param format the format of the file
     * @return a stream of lines from the file
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> stream(Path path, Format format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return streamPlainText(path);
            case ZIP:
                return streamZip(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a line stream from a plain-text file.
     *
     * @param path the path to the file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream from the first non-directory entry of a ZIP file.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from the first entry
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a line stream from a GZIP file.
     *
     * @param path the path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    private static boolean matchesMagic(Path path, int magic1, int magic2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            return in.read() == magic1 && in.read() == magic2;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return false;
    }
}
