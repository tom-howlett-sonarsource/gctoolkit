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
 * Shared utilities for discovering the format of a GC log source and for opening
 * line streams over plain, ZIP, and GZIP sources. Used by both the API and parser
 * modules so the behaviour is defined in exactly one place.
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
     * Discover the format of the source at the given path by inspecting whether it
     * is a directory and, if not, reading its leading magic bytes.
     * @param path The path to the source.
     * @return The discovered {@link LogFileFormat}.
     */
    public static LogFileFormat discover(Path path) {
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        else if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        else if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        else
            return LogFileFormat.PLAINTEXT;
    }

    /**
     * Read the first two bytes (the magic bytes) of the file at the given path and
     * compare them against the expected values.
     * @param path The path to the file.
     * @param field1 The expected value of the first byte.
     * @param field2 The expected value of the second byte.
     * @return {@code true} if the first two bytes match the expected values.
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
     * Open a line stream over the source at the given path according to the supplied format.
     * @param path The path to the source.
     * @param format The format of the source.
     * @return A stream of lines from the source.
     * @throws IOException if the source cannot be read, or the format is not streamable.
     */
    public static Stream<String> open(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }

    /**
     * Open a line stream over a plain text source.
     * @param path The path to the source.
     * @return A stream of lines from the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over the first non-directory entry of a ZIP source.
     * @param path The path to the source.
     * @return A stream of lines from the source.
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
     * Open a line stream over a GZIP source.
     * @param path The path to the source.
     * @return A stream of lines from the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
