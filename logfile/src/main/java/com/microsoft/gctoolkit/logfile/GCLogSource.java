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
 * Shared utilities for GC log sources. This provides the production IO behavior that is common
 * to the API and parser modules: discovering the format of a source from its magic bytes, sizing
 * a source in bytes, and opening plain, ZIP, and GZIP log streams.
 * <p>
 * This class holds no state; all methods are static.
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    /** First magic byte of a GZIP compressed file. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second magic byte of a GZIP compressed file. */
    public static final int GZIP_MAGIC2 = 0x8b;

    /** First magic byte of a ZIP compressed file. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second magic byte of a ZIP compressed file. */
    public static final int ZIP_MAGIC2 = 0x4b;

    /**
     * The format of a GC log source.
     */
    public enum Format {
        /** A regular, uncompressed log file. */
        PLAINTEXT,
        /** A ZIP compressed file. */
        ZIP,
        /** A GZIP compressed file. */
        GZIP,
        /** A directory, typically holding rotating log segments. */
        DIRECTORY,
        /** The format could not be determined. */
        UNKNOWN
    }

    private GCLogSource() {
    }

    /**
     * Discover the {@link Format} of the source at the given path. Directories are reported as
     * {@link Format#DIRECTORY}, compressed files are identified by their magic bytes, and anything
     * else is treated as {@link Format#PLAINTEXT}.
     * @param path The path to the source.
     * @return The discovered format.
     */
    public static Format discover(Path path) {
        if (path.toFile().isDirectory())
            return Format.DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return Format.GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return Format.ZIP;
        else
            return Format.PLAINTEXT;
    }

    /**
     * Return {@code true} if the first two bytes of the file match the supplied magic bytes.
     * @param path The path to the file.
     * @param field1 The expected first byte.
     * @param field2 The expected second byte.
     * @return {@code true} if the file begins with the supplied magic bytes.
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
     * Return the size, in bytes, of the source at the given path.
     * @param path The path to the source.
     * @return The size of the source in bytes.
     * @throws IOException if the size cannot be determined.
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a stream of the lines of the source, selecting the reader based on the discovered
     * {@link Format}.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> open(Path path) throws IOException {
        switch (discover(path)) {
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
     * Open a stream of the lines of a plain text source.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of the lines of a ZIP compressed source. The first non-directory entry is the
     * entry of interest.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
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
     * Open a stream of the lines of a GZIP compressed source.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
