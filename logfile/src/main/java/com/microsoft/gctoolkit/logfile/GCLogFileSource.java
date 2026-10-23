// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logfile;

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
 * Shared utilities for GC log sources. Centralises the production IO behaviour that was
 * previously duplicated between the API and parser modules: source discovery, byte sizing,
 * and the opening of plain, ZIP, and GZIP log streams.
 */
public final class GCLogFileSource {

    private static final Logger LOG = Logger.getLogger(GCLogFileSource.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private GCLogFileSource() {
    }

    /**
     * Discover the format of the source at the given path. A directory is reported as
     * {@link LogFileFormat#DIRECTORY}; otherwise the leading magic bytes are inspected to
     * distinguish {@link LogFileFormat#GZIP}, {@link LogFileFormat#ZIP}, and
     * {@link LogFileFormat#PLAINTEXT}.
     * @param path The path to the source.
     * @return The discovered {@link LogFileFormat}.
     */
    public static LogFileFormat discoverFormat(Path path) {
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
     * Read the leading bytes of the file and test them against the supplied magic numbers.
     * @param path The path to the file.
     * @param field1 The expected value of the first byte.
     * @param field2 The expected value of the second byte.
     * @return {@code true} if the first two bytes match the supplied values.
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
     * Open a stream of lines for the source, discovering its format first. Only readable
     * single-file formats (plain text, ZIP, and GZIP) are supported.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> stream(Path path) throws IOException {
        switch (discoverFormat(path)) {
            case PLAINTEXT:
                return streamPlainText(path);
            case ZIP:
                return streamZip(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }

    /**
     * Open a stream of lines for a plain text log file.
     * @param path The path to the file.
     * @return A stream of the lines in the file.
     * @throws IOException Thrown if the file cannot be read.
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of lines for a ZIP compressed log file. The first non-directory entry
     * is the file of interest.
     * @param path The path to the file.
     * @return A stream of the lines in the first entry of the archive.
     * @throws IOException Thrown if the file cannot be read.
     */
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a stream of lines for a GZIP compressed log file.
     * @param path The path to the file.
     * @return A stream of the lines in the file.
     * @throws IOException Thrown if the file cannot be read.
     */
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
