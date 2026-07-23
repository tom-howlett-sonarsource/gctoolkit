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
 *
 * <p>This class consolidates the duplicated IO behaviour that was previously
 * spread across the API and parser modules.</p>
 */
public final class GCLogSource {

    private static final Logger LOGGER = Logger.getLogger(GCLogSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSource() { }

    /**
     * The file format of a GC log source.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN
    }

    // ---- format detection ------------------------------------------------

    /**
     * Detect the format of the file at the given path by inspecting its
     * first two bytes (magic number) or checking whether it is a directory.
     *
     * @param path path to the log source
     * @return the detected {@link Format}
     */
    public static Format detect(Path path) {
        if (path.toFile().isDirectory()) {
            return Format.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return Format.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return Format.ZIP;
        }
        return Format.PLAINTEXT;
    }

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == expected1 && b2 == expected2;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return false;
    }

    // ---- byte sizing -----------------------------------------------------

    /**
     * Return the size of the file at the given path, in bytes.
     *
     * @param path path to the log source
     * @return size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    // ---- stream opening --------------------------------------------------

    /**
     * Open the log source at {@code path}, auto-detecting the format,
     * and return its lines as a {@link Stream}.
     *
     * @param path path to the log source
     * @return a stream of lines
     * @throws IOException if the file cannot be read or its format is unsupported
     */
    public static Stream<String> stream(Path path) throws IOException {
        Format format = detect(path);
        switch (format) {
            case PLAINTEXT:
                return streamPlainText(path);
            case ZIP:
                return streamZipFile(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unsupported log source format for: " + path);
        }
    }

    /**
     * Open a plain-text log file and return its lines as a {@link Stream}.
     *
     * @param path path to the plain-text file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-compressed log file and return the lines of the first
     * non-directory entry as a {@link Stream}.
     *
     * @param path path to the ZIP file
     * @return a stream of lines from the first entry
     * @throws IOException if the file cannot be read or contains no entries
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
     * Open a GZIP-compressed log file and return its lines as a {@link Stream}.
     *
     * @param path path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
