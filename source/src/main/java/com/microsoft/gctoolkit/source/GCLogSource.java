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
 * Shared utilities for GC log sources. This is the single home for the IO behaviour
 * that both the API and parser modules depend on:
 * <ul>
 *     <li>source discovery - detecting the {@link LogFileFormat} of a path from its leading bytes;</li>
 *     <li>byte sizing - reading the leading magic bytes and the size, in bytes, of a source;</li>
 *     <li>opening plain, ZIP and GZIP log streams as a {@link Stream} of lines.</li>
 * </ul>
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
     * Discover the format of the source at the given path by inspecting its leading bytes.
     * Directories are reported as {@link LogFileFormat#DIRECTORY}; a readable regular file that
     * matches no known magic is reported as {@link LogFileFormat#PLAINTEXT}.
     * @param path The path to the source.
     * @return The discovered {@link LogFileFormat}.
     */
    public static LogFileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        else if (hasMagicBytes(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        else if (hasMagicBytes(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        else
            return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} if the first two bytes of the file match the supplied magic bytes.
     * @param path The path to the file.
     * @param field1 The expected first byte.
     * @param field2 The expected second byte.
     * @return {@code true} if the leading bytes match.
     */
    public static boolean hasMagicBytes(Path path, int field1, int field2) {
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
     * Return the size, in bytes, of the source at the given path.
     * @param path The path to the source.
     * @return The size in bytes.
     * @throws IOException If the size cannot be read.
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a stream of lines over the source, choosing the reader based on the supplied format.
     * @param path The path to the source.
     * @param format The format of the source, as returned by {@link #detectFormat(Path)}.
     * @return A stream of the raw lines in the source.
     * @throws IOException If the source cannot be read, or the format has no line representation.
     */
    public static Stream<String> stream(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGzip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a plain text source as a stream of lines.
     * @param path The path to the source.
     * @return A stream of the raw lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP source as a stream of lines, reading from the first non-directory entry.
     * @param path The path to the source.
     * @return A stream of the raw lines in the source.
     * @throws IOException If the source cannot be read.
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
     * @return A stream of the raw lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public static Stream<String> openGzip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
