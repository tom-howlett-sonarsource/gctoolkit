// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.io;

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
 * Utilities shared by the API and parser modules for discovering GC log
 * sources, reporting their byte size, and opening line-oriented streams
 * over plain-text, ZIP, and GZIP log files.
 *
 * <p>Behavior is intentionally identical to the callers that previously
 * inlined this logic: {@code SingleGCLogFile}, {@code SafepointLogFile},
 * and {@code LogFileMetadata}.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    public static final int GZIP_MAGIC1 = 0x1F;
    public static final int GZIP_MAGIC2 = 0x8b;

    public static final int ZIP_MAGIC1 = 0x50;
    public static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSources() {
    }

    /**
     * Detect the format of a log source by inspecting the file system entry
     * and, for regular files, the first two bytes.
     *
     * @param path the source path
     * @return the detected format; {@link LogFileFormat#PLAINTEXT} when the
     *         file exists but no compressed-format magic is matched.
     */
    public static LogFileFormat discoverFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return LogFileFormat.DIRECTORY;
        }
        if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogFileFormat.GZIP;
        }
        if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogFileFormat.ZIP;
        }
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} when the first two bytes of {@code path} equal the
     * given magic-byte pair.
     *
     * @param path   the source path
     * @param field1 expected value of the first byte
     * @param field2 expected value of the second byte
     * @return {@code true} when the file starts with the given two bytes;
     *         {@code false} on mismatch or when the file cannot be read.
     */
    public static boolean magic(Path path, int field1, int field2) {
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
     * @return the file size in bytes, or {@code -1} when the size cannot be
     *         determined (e.g. the path does not refer to a regular file).
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return -1L;
        }
    }

    /**
     * Open a line stream over a plain-text log file.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over the first non-directory entry inside a ZIP log file.
     */
    public static Stream<String> openZip(Path path) throws IOException {
        @SuppressWarnings("resource")
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a line stream over a GZIP log file.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        @SuppressWarnings("resource")
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
