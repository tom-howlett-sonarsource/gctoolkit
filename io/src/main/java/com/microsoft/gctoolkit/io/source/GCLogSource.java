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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared utilities that discover a GC log source's format, report its byte size,
 * and open a line stream over plain-text, ZIP, or GZIP log files.
 *
 * <p>Kept dependency-free so both {@code gctoolkit-api} and {@code gctoolkit-parser}
 * can consume it without pulling in JVM-model or parser types.</p>
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;
    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Discover the format of a GC log source rooted at {@code path}.
     *
     * @param path the source path (a file or directory)
     * @return the detected {@link SourceFormat}; {@link SourceFormat#UNKNOWN}
     *         only when magic-byte inspection fails with an unexpected error
     */
    public static SourceFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return SourceFormat.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return SourceFormat.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return SourceFormat.ZIP;
        }
        return SourceFormat.PLAINTEXT;
    }

    /**
     * Return the size of {@code path} in bytes.
     *
     * @param path the file to size
     * @return size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a line stream over a plain-text log file.
     */
    public static Stream<String> openPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over a ZIP-compressed log file, reading the first
     * non-directory entry.
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
     * Open a line stream over a GZIP-compressed log file.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Open a line stream over {@code path} using the given detected format.
     *
     * @param path   the source file
     * @param format the format previously discovered for {@code path}
     * @return a line stream
     * @throws IOException if the format is not readable (directory, unknown,
     *                     or opening the stream fails)
     */
    public static Stream<String> open(Path path, SourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlainText(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static boolean hasMagic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, ioe.getMessage());
        }
        return false;
    }
}
