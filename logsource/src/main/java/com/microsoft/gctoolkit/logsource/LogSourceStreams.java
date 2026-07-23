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
 * Shared utilities for GC log source discovery, format detection, byte sizing,
 * and opening plain-text, ZIP, and GZIP log streams.
 */
public final class LogSourceStreams {

    private static final Logger LOG = Logger.getLogger(LogSourceStreams.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private LogSourceStreams() { }

    /**
     * Detect the {@link FileFormat} of the file at the given path by inspecting
     * its magic bytes and file-system attributes.
     *
     * @param path the path to inspect
     * @return the detected format, or {@link FileFormat#UNKNOWN} on failure
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        }
        if (magicMatch(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        }
        if (magicMatch(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        }
        return FileFormat.PLAINTEXT;
    }

    /**
     * Return the size, in bytes, of the file at the given path.
     *
     * @param path the file whose size is requested
     * @return the file size in bytes
     * @throws IOException if the file does not exist or cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a line stream from a plain-text file.
     *
     * @param path the file to read
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> lines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream from a ZIP file, reading the first non-directory entry.
     *
     * @param path the ZIP file to read
     * @return a stream of lines from the first non-directory entry
     * @throws IOException if the file cannot be opened or contains no entries
     */
    public static Stream<String> linesFromZip(Path path) throws IOException {
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
     * @param path the GZIP file to read
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> linesFromGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Auto-detect the file format and open the appropriate line stream.
     *
     * @param path the file to read
     * @return a stream of lines
     * @throws IOException if the format is unsupported or the file cannot be read
     */
    public static Stream<String> stream(Path path) throws IOException {
        FileFormat format = detectFormat(path);
        return stream(path, format);
    }

    /**
     * Open a line stream for the given path using the specified format.
     *
     * @param path   the file to read
     * @param format the pre-determined file format
     * @return a stream of lines
     * @throws IOException if the format is unsupported or the file cannot be read
     */
    public static Stream<String> stream(Path path, FileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return lines(path);
            case ZIP:
                return linesFromZip(path);
            case GZIP:
                return linesFromGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static boolean magicMatch(Path path, int expected1, int expected2) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            int byte1 = fis.read();
            int byte2 = fis.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }
}
