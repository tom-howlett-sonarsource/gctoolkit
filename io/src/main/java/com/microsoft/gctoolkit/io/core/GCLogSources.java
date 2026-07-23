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
 * This class centralises the IO behaviour that was previously duplicated across the
 * API and parser modules.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSources() {
    }

    /**
     * Detect the format of the file at the given path by inspecting magic bytes.
     *
     * @param path the path to inspect
     * @return the detected {@link FileFormat}
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        } else if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        } else if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        } else {
            return FileFormat.PLAINTEXT;
        }
    }

    /**
     * Open a plain-text log file as a stream of lines.
     *
     * @param path the path to the file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry in a ZIP file as a stream of lines.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from the first log entry
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
     * Open a GZIP-compressed log file as a stream of lines.
     *
     * @param path the path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Return the size of the file in bytes.
     *
     * @param path the path to the file
     * @return the file size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long fileSize(Path path) throws IOException {
        return Files.size(path);
    }

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
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
