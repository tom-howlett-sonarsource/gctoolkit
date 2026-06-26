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
 * Shared utilities for discovering the format of a GC log source and for streaming its
 * contents a line at a time. The API and parser modules both build on these helpers so
 * the log discovery and IO logic lives in a single place.
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSource() {
        // utility class
    }

    /**
     * Determine the {@link FileFormat} of the source at the given path. A directory is
     * reported as {@link FileFormat#DIRECTORY}; otherwise the leading magic bytes are used
     * to distinguish GZip and Zip archives from plain text.
     * @param path The path to the log source.
     * @return The detected file format, defaulting to {@link FileFormat#PLAINTEXT}.
     */
    public static FileFormat formatOf(Path path) {
        if (path.toFile().isDirectory())
            return FileFormat.DIRECTORY;
        else if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return FileFormat.GZIP;
        else if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return FileFormat.ZIP;
        else
            return FileFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} if the first two bytes of the file match the given magic bytes.
     * @param path The path to the file.
     * @param field1 The expected value of the first byte.
     * @param field2 The expected value of the second byte.
     * @return {@code true} if both leading bytes match.
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
     * Stream a plain text log file, one line at a time.
     * @param path The path to the file.
     * @return A stream of lines from the file.
     * @throws IOException Thrown if the file cannot be opened.
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the first non-directory entry of a Zip file, one line at a time.
     * @param path The path to the Zip file.
     * @return A stream of lines from the first entry.
     * @throws IOException Thrown if the file cannot be opened.
     */
    public static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Stream a GZip file, one line at a time.
     * @param path The path to the GZip file.
     * @return A stream of lines from the file.
     * @throws IOException Thrown if the file cannot be opened.
     */
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
