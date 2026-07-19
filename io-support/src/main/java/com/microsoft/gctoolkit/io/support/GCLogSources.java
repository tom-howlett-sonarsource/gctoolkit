// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

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
 * Shared utilities for GC log source handling: format discovery (magic byte
 * detection), byte sizing, and opening plain text, ZIP, and GZIP log streams.
 * <p>
 * These primitives previously lived (duplicated) inside the api and parser
 * modules; consolidating them here lets both modules share exactly one
 * production implementation.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSources() {
    }

    /**
     * Discover the {@link SourceFormat} of the file at {@code path} by inspecting
     * its first two bytes (or classifying the path as a directory).
     *
     * @param path the source path.
     * @return the detected format, {@link SourceFormat#UNKNOWN} if the path does
     *         not exist or cannot be read.
     */
    public static SourceFormat detect(Path path) {
        if (path == null) {
            return SourceFormat.UNKNOWN;
        }
        if (Files.isDirectory(path)) {
            return SourceFormat.DIRECTORY;
        }
        if (!Files.isRegularFile(path)) {
            return SourceFormat.UNKNOWN;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return SourceFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return SourceFormat.ZIP;
        }
        return SourceFormat.PLAINTEXT;
    }

    /**
     * Return the size in bytes of the regular file at {@code path}.
     *
     * @param path the source path.
     * @return the file size in bytes, or {@code -1} if the size cannot be read
     *         (for example, the path is a directory or does not exist).
     */
    public static long byteSize(Path path) {
        if (path == null) {
            return -1L;
        }
        try {
            if (!Files.isRegularFile(path)) {
                return -1L;
            }
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to determine size of " + path, ioe);
            return -1L;
        }
    }

    /**
     * Open a stream of lines over a plain text log file.
     *
     * @param path the source path.
     * @return a lazy line stream.
     * @throws IOException if the file cannot be opened.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of lines over the first non-directory entry of a ZIP archive.
     *
     * @param path the source path.
     * @return a lazy line stream.
     * @throws IOException if the archive cannot be opened or contains no readable entry.
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry = zipStream.getNextEntry();
        while (entry != null && entry.isDirectory()) {
            entry = zipStream.getNextEntry();
        }
        if (entry == null) {
            zipStream.close();
            throw new IOException("No readable entry in ZIP file " + path);
        }
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a stream of lines over a GZIP-compressed log file.
     *
     * @param path the source path.
     * @return a lazy line stream.
     * @throws IOException if the file cannot be opened.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Open a line stream, dispatching on {@link #detect(Path)}.
     *
     * @param path the source path.
     * @return a lazy line stream.
     * @throws IOException if the format is not readable or opening fails.
     */
    public static Stream<String> openLines(Path path) throws IOException {
        SourceFormat format = detect(path);
        switch (format) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path + " (format=" + format + ")");
        }
    }

    private static boolean matchesMagic(Path path, int field1, int field2) {
        try (FileInputStream reader = new FileInputStream(path.toFile())) {
            int magicByte1 = reader.read();
            int magicByte2 = reader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return false;
        }
    }
}
