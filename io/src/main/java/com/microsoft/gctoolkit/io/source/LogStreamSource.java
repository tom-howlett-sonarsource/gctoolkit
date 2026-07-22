// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
 * Shared utilities for GC log source discovery, format detection, byte sizing,
 * and opening plain, ZIP, and GZIP log streams.
 */
public final class LogStreamSource {

    private LogStreamSource() {
    }

    /**
     * The format of a log file, determined by magic-byte inspection.
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
     * Detect the format of the file at the given path by inspecting magic bytes.
     * Directories are identified by {@link java.io.File#isDirectory()}.
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
        }
        return FileFormat.PLAINTEXT;
    }

    /**
     * Check whether the first two bytes of a file match the given magic values.
     *
     * @param path   the file to inspect
     * @param magic1 expected first byte
     * @param magic2 expected second byte
     * @return {@code true} if the file starts with the given bytes
     */
    public static boolean matchesMagic(Path path, int magic1, int magic2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == magic1 && b2 == magic2;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Return the size of the file in bytes, or {@code -1} if the size cannot be determined.
     *
     * @param path the file to measure
     * @return the size in bytes, or {@code -1} on error
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1L;
        }
    }

    /**
     * Open a line stream for a single log file. The format is auto-detected
     * via {@link #detectFormat(Path)}.
     *
     * @param path the file to stream
     * @return a {@link Stream} of lines from the file
     * @throws IOException if the file cannot be read or has an unsupported format
     */
    public static Stream<String> stream(Path path) throws IOException {
        FileFormat format = detectFormat(path);
        return stream(path, format);
    }

    /**
     * Open a line stream for a single log file whose format has already been determined.
     *
     * @param path   the file to stream
     * @param format the pre-detected format
     * @return a {@link Stream} of lines from the file
     * @throws IOException if the file cannot be read or has an unsupported format
     */
    public static Stream<String> stream(Path path, FileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamZipFile(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unable to stream file: " + path);
        }
    }

    /**
     * Open a line stream for a ZIP-compressed log file. Skips directory entries
     * and reads the first file entry. The returned stream, when closed, will
     * close the underlying ZIP and buffered reader resources.
     *
     * @param path the ZIP file to stream
     * @return a {@link Stream} of lines from the first file entry
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("java:S2095") // caller closes the returned stream
    public static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a line stream for a GZIP-compressed log file. The returned stream,
     * when closed, will close the underlying GZIP and buffered reader resources.
     *
     * @param path the GZIP file to stream
     * @return a {@link Stream} of lines from the decompressed content
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("java:S2095") // caller closes the returned stream
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
