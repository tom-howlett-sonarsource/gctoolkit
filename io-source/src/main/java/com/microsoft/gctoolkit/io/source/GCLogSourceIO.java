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
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared IO utilities for GC log source discovery, magic-byte sizing checks,
 * and opening plain, ZIP, and GZIP GC log streams. These helpers are shared
 * between the API and parser modules so the same production behavior is
 * used everywhere.
 */
public final class GCLogSourceIO {

    private static final Logger LOG = Logger.getLogger(GCLogSourceIO.class.getName());

    public static final int GZIP_MAGIC1 = 0x1F;
    public static final int GZIP_MAGIC2 = 0x8b;

    public static final int ZIP_MAGIC1 = 0x50;
    public static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSourceIO() {
    }

    /**
     * Detect the {@link GCLogFormat} of the file at {@code path} by probing
     * for a directory and then inspecting the first two magic bytes.
     * @param path The path to a GC log source.
     * @return The detected format, never {@code null}.
     */
    public static GCLogFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return GCLogFormat.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return GCLogFormat.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return GCLogFormat.ZIP;
        }
        return GCLogFormat.PLAINTEXT;
    }

    /**
     * Read the first two bytes of {@code path} and compare them against a
     * magic-byte prefix. This is the sizing check used for GC log format
     * discovery (ZIP, GZIP).
     * @param path The path to a GC log source.
     * @param field1 First expected magic byte.
     * @param field2 Second expected magic byte.
     * @return {@code true} when both magic bytes match.
     */
    public static boolean hasMagic(Path path, int field1, int field2) {
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
     * Open a plain-text log file for line-by-line streaming.
     * @param path The path to a plain-text GC log.
     * @return A stream of lines from {@code path}.
     * @throws IOException If the file cannot be opened.
     */
    public static Stream<String> openPlainStream(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-packaged GC log and return a line stream over the first
     * non-directory entry.
     * @param path The path to a ZIP GC log.
     * @return A stream of lines from the first non-directory entry.
     * @throws IOException If the file cannot be opened.
     */
    @SuppressWarnings("resource")
    public static Stream<String> openZipStream(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP-packaged GC log and return a line stream over its content.
     * @param path The path to a GZIP GC log.
     * @return A stream of lines from the decompressed content.
     * @throws IOException If the file cannot be opened.
     */
    @SuppressWarnings("resource")
    public static Stream<String> openGZipStream(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
