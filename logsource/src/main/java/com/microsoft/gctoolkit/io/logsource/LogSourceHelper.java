// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
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
 * Shared utilities for GC log source discovery, format detection, byte sizing,
 * and opening plain-text, ZIP, and GZIP log streams.
 */
public final class LogSourceHelper {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private LogSourceHelper() {
        // utility class
    }

    /**
     * Detect the format of a file by inspecting its magic bytes and filesystem attributes.
     *
     * @param path the path to inspect
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
        if (path.toFile().isFile()) {
            return FileFormat.PLAINTEXT;
        }
        return FileFormat.UNKNOWN;
    }

    /**
     * Return the size, in bytes, of the given file.
     *
     * @param path the path to size
     * @return the file size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long fileSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a plain-text file as a stream of lines.
     *
     * @param path the path to the plain-text file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP file and stream lines from the first non-directory entry.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from the first file entry
     * @throws IOException if the file cannot be read or contains no file entries
     */
    @SuppressWarnings("java:S2095") // resources are closed via Stream.onClose
    public static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                throw new IOException("ZIP file contains no file entries: " + path);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
            return reader.lines().onClose(() -> closeQuietly(reader));
        } catch (IOException e) {
            zipStream.close();
            throw e;
        }
    }

    /**
     * Open a GZIP file and stream its lines.
     *
     * @param path the path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("java:S2095") // resources are closed via Stream.onClose
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Auto-detect the file format and open an appropriate line stream.
     * Directories are not supported by this method.
     *
     * @param path the path to the log file
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
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
                throw new IOException("Unable to stream file with format " + format + ": " + path);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            int byte1 = fis.read();
            int byte2 = fis.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException e) {
            return false;
        }
    }
}
