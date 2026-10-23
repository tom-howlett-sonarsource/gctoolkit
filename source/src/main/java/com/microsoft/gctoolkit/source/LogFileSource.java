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
 * Shared production utilities for GC log sources. Centralises the behaviour that
 * was previously duplicated across the API and parser modules:
 * <ul>
 *     <li>source discovery - classifying a path by its leading (magic) bytes,</li>
 *     <li>byte sizing - reporting the size of a source on disk,</li>
 *     <li>opening plain, ZIP, and GZIP log streams.</li>
 * </ul>
 */
public final class LogFileSource {

    private static final Logger LOG = Logger.getLogger(LogFileSource.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private LogFileSource() {
    }

    /**
     * Discover the format of a source by inspecting its leading bytes. A path that
     * is a directory is reported as {@link SourceFormat#DIRECTORY}; anything whose
     * magic bytes are not recognised as a compressed archive is treated as
     * {@link SourceFormat#PLAINTEXT}.
     * @param path The path to the source.
     * @return The discovered {@link SourceFormat}.
     */
    public static SourceFormat discoverFormat(Path path) {
        if (path.toFile().isDirectory())
            return SourceFormat.DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return SourceFormat.GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return SourceFormat.ZIP;
        else
            return SourceFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} if the first two bytes of the source match the supplied
     * magic byte values.
     * @param path The path to the source.
     * @param field1 The expected first byte.
     * @param field2 The expected second byte.
     * @return {@code true} if both leading bytes match.
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
     * Return the size, in bytes, of the source on disk. Returns {@code 0} if the
     * size cannot be determined.
     * @param path The path to the source.
     * @return The size of the source in bytes, or {@code 0} if unknown.
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return 0L;
        }
    }

    /**
     * Open a stream of the lines in the source, selecting the reader appropriate
     * for the supplied {@link SourceFormat}.
     * @param path The path to the source.
     * @param format The discovered format of the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read, or the format is not a
     * readable log stream.
     */
    public static Stream<String> stream(Path path, SourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamZipFile(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a stream of the lines in the source, discovering its format first.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> stream(Path path) throws IOException {
        return stream(path, discoverFormat(path));
    }

    private static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    private static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
