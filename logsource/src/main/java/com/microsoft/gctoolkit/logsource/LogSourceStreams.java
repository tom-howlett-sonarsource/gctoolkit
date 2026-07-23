// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
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
 * Utility methods for detecting GC log file formats and opening log streams.
 * Shared by the api and parser modules to avoid duplicating IO logic.
 */
public final class LogSourceStreams {

    private static final Logger LOGGER = Logger.getLogger(LogSourceStreams.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private LogSourceStreams() {}

    /**
     * Recognized file formats for GC log sources.
     */
    public enum FileFormat {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN
    }

    /**
     * Detect the format of the file at the given path by reading its magic bytes.
     * @param path the path to examine
     * @return the detected format
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        }
        try (var in = Files.newInputStream(path)) {
            int byte1 = in.read();
            int byte2 = in.read();
            if (byte1 == GZIP_MAGIC1 && byte2 == GZIP_MAGIC2) {
                return FileFormat.GZIP;
            }
            if (byte1 == ZIP_MAGIC1 && byte2 == ZIP_MAGIC2) {
                return FileFormat.ZIP;
            }
            return FileFormat.PLAINTEXT;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
            return FileFormat.UNKNOWN;
        }
    }

    /**
     * Open a stream of lines from the given path, auto-detecting the format.
     * @param path the log file path
     * @return a stream of lines
     * @throws IOException if the format is unsupported or the file cannot be read
     */
    public static Stream<String> stream(Path path) throws IOException {
        FileFormat format = detectFormat(path);
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
     * Stream lines from a plain text file.
     * @param path the file path
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream lines from a ZIP file, reading the first non-directory entry.
     * @param path the ZIP file path
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
     * Stream lines from a GZIP-compressed file.
     * @param path the GZIP file path
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Return the byte size of the file at the given path.
     * @param path the file path
     * @return the file size in bytes
     * @throws IOException if the file size cannot be determined
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }
}
