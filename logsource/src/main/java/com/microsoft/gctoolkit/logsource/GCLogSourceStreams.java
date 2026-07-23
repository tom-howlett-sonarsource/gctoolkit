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
 * Shared utility for GC log source format detection and stream opening.
 * Supports plain-text, ZIP, and GZIP log files.
 */
public final class GCLogSourceStreams {

    private static final Logger LOGGER = Logger.getLogger(GCLogSourceStreams.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * The supported file formats for GC log sources.
     */
    public enum FileFormat {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN
    }

    private GCLogSourceStreams() {
        // utility class
    }

    /**
     * Detect the format of a file by inspecting magic bytes and file type.
     * @param path The path to inspect.
     * @return The detected {@link FileFormat}.
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        } else if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        } else if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        }
        return FileFormat.PLAINTEXT;
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

    /**
     * Open a plain-text file as a stream of lines.
     * @param path The path to the file.
     * @return A stream of lines from the file.
     * @throws IOException if the file cannot be read.
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP file and stream lines from the first non-directory entry.
     * @param path The path to the ZIP file.
     * @return A stream of lines from the first non-directory entry.
     * @throws IOException if the file cannot be read.
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
     * Open a GZIP file and stream lines from it.
     * @param path The path to the GZIP file.
     * @return A stream of lines from the GZIP file.
     * @throws IOException if the file cannot be read.
     */
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Auto-detect the format of a file and stream its lines.
     * Supports plain-text, ZIP, and GZIP formats.
     * @param path The path to the file.
     * @return A stream of lines from the file.
     * @throws IOException if the file cannot be read or has an unsupported format.
     */
    public static Stream<String> stream(Path path) throws IOException {
        FileFormat format = detectFormat(path);
        if (format == FileFormat.PLAINTEXT) {
            return streamPlainText(path);
        } else if (format == FileFormat.ZIP) {
            return streamZipFile(path);
        } else if (format == FileFormat.GZIP) {
            return streamGZipFile(path);
        }
        throw new IOException("Unable to stream: " + path);
    }

    /**
     * Return the size in bytes of the file at the given path.
     * @param path The path to the file.
     * @return The file size in bytes.
     * @throws IOException if the file size cannot be determined.
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }
}
