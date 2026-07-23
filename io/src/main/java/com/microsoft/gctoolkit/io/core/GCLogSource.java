// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.core;

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
 * Shared utilities for GC log source discovery, byte sizing, and stream opening.
 * <p>
 * Supports plain-text, ZIP-compressed, and GZIP-compressed log files.
 */
public final class GCLogSource {

    private static final Logger LOGGER = Logger.getLogger(GCLogSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSource() {
        // utility class
    }

    /**
     * Detect the on-disk format of a GC log source by inspecting magic bytes.
     *
     * @param path path to the log file or directory
     * @return the detected {@link FileFormat}
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        }
        return FileFormat.PLAINTEXT;
    }

    /**
     * Return the size of the file in bytes.
     *
     * @param path path to the file
     * @return size in bytes
     * @throws IOException if the file cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a plain-text log file as a stream of lines.
     *
     * @param path path to the plain-text file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> openPlainTextStream(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-compressed log file and stream lines from the first non-directory entry.
     *
     * @param path path to the ZIP file
     * @return a stream of lines from the first log entry
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("resource") // resources are closed when the caller closes the returned stream
    public static Stream<String> openZipStream(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ignored) {
                // closing silently
            }
        });
    }

    /**
     * Open a GZIP-compressed log file as a stream of lines.
     *
     * @param path path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("resource") // resources are closed when the caller closes the returned stream
    public static Stream<String> openGZipStream(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ignored) {
                // closing silently
            }
        });
    }

    /**
     * Open a log file as a stream of lines, choosing the right decompression based on format.
     *
     * @param path   path to the log file
     * @param format the file format
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> openStream(Path path, FileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlainTextStream(path);
            case ZIP:
                return openZipStream(path);
            case GZIP:
                return openGZipStream(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            int byte1 = fis.read();
            int byte2 = fis.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return false;
    }
}
