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
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared utilities for discovering and opening GC log sources.
 * <p>
 * Provides three groups of behavior previously duplicated between the API
 * and parser modules:
 * <ul>
 *   <li>Source discovery: listing entries under a directory.</li>
 *   <li>Byte sizing: reading the leading magic bytes used to distinguish
 *       plain-text, ZIP, and GZIP inputs.</li>
 *   <li>Opening streams for plain-text, ZIP, and GZIP log files.</li>
 * </ul>
 * This class is a stateless collection of static helpers and cannot be
 * instantiated.
 */
public final class GCLogSources {

    /** First byte of the GZIP magic number. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second byte of the GZIP magic number. */
    public static final int GZIP_MAGIC2 = 0x8b;
    /** First byte of the ZIP local file header signature. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second byte of the ZIP local file header signature. */
    public static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSources() {
    }

    /**
     * Read the first two bytes of the file at {@code path} and compare them
     * to {@code field1} and {@code field2}. Returns {@code false} on any
     * IO error.
     * @param path path to a regular file.
     * @param field1 expected value of the first byte.
     * @param field2 expected value of the second byte.
     * @return {@code true} if the file's leading two bytes match.
     */
    public static boolean matchesMagic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            return false;
        }
    }

    /**
     * Test whether the file begins with the GZIP magic number.
     * @param path path to a regular file.
     * @return {@code true} if the file is GZIP-compressed.
     */
    public static boolean isGZip(Path path) {
        return matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2);
    }

    /**
     * Test whether the file begins with the ZIP local file header signature.
     * @param path path to a regular file.
     * @return {@code true} if the file is ZIP-compressed.
     */
    public static boolean isZip(Path path) {
        return matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2);
    }

    /**
     * Enumerate the entries directly under {@code directory}. The returned
     * stream is backed by an open directory handle and must be closed by
     * the caller (typically via try-with-resources).
     * @param directory a directory to list.
     * @return a stream of the entries in {@code directory}.
     * @throws IOException if the directory cannot be read.
     */
    public static Stream<Path> listSources(Path directory) throws IOException {
        return Files.list(directory);
    }

    /**
     * Open the plain-text log file at {@code path} for line-oriented reading.
     * @param path the log file.
     * @return a stream of the lines in the file.
     * @throws IOException if the file cannot be opened.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the ZIP-compressed log file at {@code path}, skipping directory
     * entries and returning a line stream over the first non-directory entry.
     * @param path the ZIP log file.
     * @return a stream of the lines in the first non-directory entry.
     * @throws IOException if the file cannot be opened.
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
     * Open the GZIP-compressed log file at {@code path} for line-oriented reading.
     * @param path the GZIP log file.
     * @return a stream of the lines in the file.
     * @throws IOException if the file cannot be opened.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
