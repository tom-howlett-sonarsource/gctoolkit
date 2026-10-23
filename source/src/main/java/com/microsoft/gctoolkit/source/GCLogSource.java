// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

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
 * Shared utilities for GC log sources rooted in the file system. Centralizes the
 * low-level IO behavior that is common to the API and parser modules:
 * <ul>
 *     <li>source/format discovery (plain text, ZIP, GZIP, directory),</li>
 *     <li>byte sizing of a source file,</li>
 *     <li>opening plain, ZIP, and GZIP log streams.</li>
 * </ul>
 * For ZIP and GZIP sources the first (non-directory) entry is the content of interest.
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    // The leading two bytes used to identify GZIP and ZIP sources.
    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;
    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private static final int MAGIC_LENGTH = 2;

    private GCLogSource() {
    }

    /**
     * The format of a GC log source.
     */
    public enum Format {
        /** A directory containing one or more log files. */
        DIRECTORY,
        /** A GZIP compressed file. */
        GZIP,
        /** A ZIP compressed file. */
        ZIP,
        /** A regular, uncompressed text file. */
        PLAINTEXT
    }

    /**
     * Return the number of bytes in the source file.
     * @param path The path to the source.
     * @return The size of the source, in bytes.
     * @throws IOException if the size cannot be determined.
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Discover the format of the source by inspecting its leading bytes. A directory is
     * reported as {@link Format#DIRECTORY}. A source that cannot be read, or that does not
     * carry a recognized magic signature, is reported as {@link Format#PLAINTEXT}.
     * @param path The path to the source.
     * @return The discovered {@link Format}.
     */
    public static Format detectFormat(Path path) {
        if (path.toFile().isDirectory())
            return Format.DIRECTORY;
        try {
            if (byteSize(path) >= MAGIC_LENGTH) {
                if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
                    return Format.GZIP;
                if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
                    return Format.ZIP;
            }
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return Format.PLAINTEXT;
    }

    private static boolean magic(Path path, int field1, int field2) {
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
     * Open the source as a stream of lines, discovering its format first.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> open(Path path) throws IOException {
        switch (detectFormat(path)) {
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

    /**
     * Open a plain text source as a stream of lines.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> openPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP source as a stream of lines, using the first non-directory entry.
     * @param path The path to the source.
     * @return A stream of the lines in the first entry.
     * @throws IOException if the source cannot be read.
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
     * Open a GZIP source as a stream of lines.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
