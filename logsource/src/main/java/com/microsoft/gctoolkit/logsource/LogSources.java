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
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared utilities for GC log source format detection, byte sizing,
 * and opening plain-text, ZIP, and GZIP log streams.
 */
public final class LogSources {

    private LogSources() {
        // utility class
    }

    /**
     * The format of a log source file.
     */
    public enum FileFormat {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN
    }

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Detect the format of a log source by inspecting magic bytes.
     * @param path the path to inspect
     * @return the detected {@link FileFormat}
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        }
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            int b1 = fis.read();
            int b2 = fis.read();
            if (b1 == GZIP_MAGIC1 && b2 == GZIP_MAGIC2) {
                return FileFormat.GZIP;
            }
            if (b1 == ZIP_MAGIC1 && b2 == ZIP_MAGIC2) {
                return FileFormat.ZIP;
            }
            return FileFormat.PLAINTEXT;
        } catch (IOException e) {
            return FileFormat.UNKNOWN;
        }
    }

    /**
     * Return the size in bytes of the file at the given path.
     * @param path the file path
     * @return the file size in bytes
     * @throws IOException if an I/O error occurs
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a plain-text log file as a stream of lines.
     * @param path the file path
     * @return a stream of lines
     * @throws IOException if an I/O error occurs
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP log file and stream lines from the first non-directory entry.
     * The caller should close the returned stream to release the underlying resources.
     * @param path the file path
     * @return a stream of lines
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("java:S2095") // Resource lifecycle is transferred to the caller via onClose
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
            return reader.lines().onClose(() -> closeQuietly(reader));
        } catch (IOException e) {
            closeQuietly(zipStream);
            throw e;
        }
    }

    /**
     * Open a GZIP log file as a stream of lines.
     * The caller should close the returned stream to release the underlying resources.
     * @param path the file path
     * @return a stream of lines
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("java:S2095") // Resource lifecycle is transferred to the caller via onClose
    public static Stream<String> streamGZip(Path path) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)))));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // best-effort close
        }
    }

    /**
     * Auto-detect the file format and open the appropriate stream.
     * @param path the file path
     * @return a stream of lines
     * @throws IOException if the format is unrecognised or an I/O error occurs
     */
    public static Stream<String> stream(Path path) throws IOException {
        FileFormat format = detectFormat(path);
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
}
