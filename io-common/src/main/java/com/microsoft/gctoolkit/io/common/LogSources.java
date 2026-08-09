// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.common;

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
 * Shared production utilities for GC log source discovery, byte-level
 * inspection, and opening plain, ZIP, and GZIP log streams.
 * <p>
 * Callers in {@code gctoolkit-api} and {@code gctoolkit-parser} share this
 * implementation so that format detection and stream construction stay in
 * lock-step across the two modules.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {
    }

    /**
     * Discover the format of {@code path} by checking whether it is a directory
     * and, if not, inspecting the first two bytes of the file.
     * @param path source path
     * @return the {@link SourceFormat} for the source
     */
    public static SourceFormat discoverFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return SourceFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return SourceFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return SourceFormat.ZIP;
        }
        return SourceFormat.PLAINTEXT;
    }

    /**
     * Byte size of the file at {@code path}. Returns {@code 0} for directories
     * or when the size cannot be determined.
     * @param path file path
     * @return the size in bytes
     */
    public static long byteSize(Path path) {
        try {
            if (path.toFile().isDirectory()) {
                return 0L;
            }
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return 0L;
        }
    }

    /**
     * {@code true} if the file at {@code path} starts with the given two magic
     * bytes.
     * @param path file path
     * @param field1 first expected byte
     * @param field2 second expected byte
     * @return {@code true} if both bytes match; {@code false} on mismatch or IO error
     */
    public static boolean matchesMagic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Open the file at {@code path} as a stream of text lines, dispatching on
     * the discovered {@link SourceFormat}.
     * @param path source path
     * @return stream of lines
     * @throws IOException if the source cannot be opened or the format cannot be handled
     */
    public static Stream<String> open(Path path) throws IOException {
        return open(path, discoverFormat(path));
    }

    /**
     * Open the file at {@code path} using an already-discovered format.
     * @param path source path
     * @param format previously discovered format
     * @return stream of lines
     * @throws IOException if the source cannot be opened or the format cannot be handled
     */
    public static Stream<String> open(Path path, SourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGzip(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }

    /**
     * Stream the plaintext file at {@code path}, one line at a time.
     * @param path source path
     * @return stream of lines
     * @throws IOException on IO error
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the ZIP file at {@code path}, one line at a time, starting from
     * the first non-directory entry.
     * @param path source path
     * @return stream of lines
     * @throws IOException on IO error
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Stream the GZIP file at {@code path}, one line at a time.
     * @param path source path
     * @return stream of lines
     * @throws IOException on IO error
     */
    public static Stream<String> openGzip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
