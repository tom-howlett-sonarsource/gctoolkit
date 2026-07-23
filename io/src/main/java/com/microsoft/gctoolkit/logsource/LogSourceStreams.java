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
 * Shared utilities for detecting GC log source formats and opening streams
 * over plain-text, ZIP, and GZIP log files.
 */
public final class LogSourceStreams {

    private static final Logger LOGGER = Logger.getLogger(LogSourceStreams.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private LogSourceStreams() {}

    /**
     * Detect the format of the file at the given path by reading its magic bytes.
     * Directories are identified by file-system metadata; ZIP and GZIP files by
     * their two-byte magic headers; everything else is assumed to be plain text.
     *
     * @param path the path to inspect
     * @return the detected {@link FileFormat}
     */
    public static FileFormat detect(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        } else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        } else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        } else {
            return FileFormat.PLAINTEXT;
        }
    }

    /**
     * Open a line stream over the given path, auto-detecting the file format.
     *
     * @param path the path to stream
     * @return a stream of lines
     * @throws IOException if the file cannot be read or has an unsupported format
     */
    public static Stream<String> stream(Path path) throws IOException {
        return stream(path, detect(path));
    }

    /**
     * Open a line stream over the given path using the specified format.
     *
     * @param path   the path to stream
     * @param format the file format
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> stream(Path path, FileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return streamPlainText(path);
            case ZIP:
                return streamZipFile(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a line stream over a plain-text file.
     *
     * @param path the path to the file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over a ZIP file. Directory entries are skipped; the
     * first non-directory entry is streamed.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from the first non-directory entry
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a line stream over a GZIP file.
     *
     * @param path the path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    static boolean magic(Path path, int expected1, int expected2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == expected1 && b2 == expected2;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return false;
    }
}
