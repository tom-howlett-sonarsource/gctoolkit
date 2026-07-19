// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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
 * Shared IO utilities for GC log sources. Consolidates the plain, ZIP and GZIP
 * stream-opening logic, byte sizing, and directory discovery that was previously
 * duplicated between the API and parser modules.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    /** First byte of the GZIP magic number. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second byte of the GZIP magic number. */
    public static final int GZIP_MAGIC2 = 0x8B;
    /** First byte of the ZIP magic number ({@code 'P'}). */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second byte of the ZIP magic number ({@code 'K'}). */
    public static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSources() {
        // utility class
    }

    /** Detected log source format. */
    public enum Format {
        PLAINTEXT,
        ZIP,
        GZIP,
        DIRECTORY,
        UNKNOWN
    }

    /**
     * Classify a log source by its on-disk shape. Directories are reported as
     * {@link Format#DIRECTORY}; regular files are classified by their two leading
     * magic bytes; anything that cannot be read is reported as {@link Format#UNKNOWN}.
     *
     * @param path the path to classify
     * @return the detected format
     */
    public static Format detect(Path path) {
        if (path == null) {
            return Format.UNKNOWN;
        }
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (!Files.isRegularFile(path)) {
            return Format.UNKNOWN;
        }
        int[] header = readMagic(path);
        if (header[0] == GZIP_MAGIC1 && header[1] == GZIP_MAGIC2) {
            return Format.GZIP;
        }
        if (header[0] == ZIP_MAGIC1 && header[1] == ZIP_MAGIC2) {
            return Format.ZIP;
        }
        return Format.PLAINTEXT;
    }

    /**
     * Compare a file's first two bytes against the supplied magic bytes.
     *
     * @param path the file to inspect
     * @param magic1 expected first byte
     * @param magic2 expected second byte
     * @return {@code true} if the file's leading two bytes match; {@code false} on
     *     mismatch or read failure
     */
    public static boolean hasMagic(Path path, int magic1, int magic2) {
        int[] header = readMagic(path);
        return header[0] == magic1 && header[1] == magic2;
    }

    /**
     * The byte length of the file at {@code path}.
     *
     * @param path a regular file
     * @return the size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * List the entries of a directory as a stream of {@link Path}s. Callers must
     * close the returned stream (typically via try-with-resources) to release the
     * underlying directory handle.
     *
     * @param directory a directory to enumerate
     * @return a stream of the direct children of {@code directory}
     * @throws IOException if the directory cannot be opened
     */
    public static Stream<Path> listDirectory(Path directory) throws IOException {
        return Files.list(directory);
    }

    /**
     * Open a plain-text log file as a line stream.
     *
     * @param path an uncompressed log file
     * @return a stream of the file's lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry of a ZIP archive as a line stream. Mirrors
     * the behavior previously duplicated in {@code SingleGCLogFile} and
     * {@code SafepointLogFile}: the underlying {@link ZipInputStream} is advanced
     * past directory entries and its first file entry is read.
     *
     * @param path a ZIP archive containing log entries
     * @return a stream of lines from the first non-directory entry
     * @throws IOException if the archive cannot be opened
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
     * Open a GZIP-compressed log file as a line stream.
     *
     * @param path a GZIP file
     * @return a stream of lines from the decompressed content
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openGzip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    private static int[] readMagic(Path path) {
        int[] result = {-1, -1};
        try (InputStream in = Files.newInputStream(path)) {
            result[0] = in.read();
            result[1] = in.read();
        } catch (IOException ioe) {
            LOG.log(Level.FINE, "Unable to read magic bytes from " + path, ioe);
        }
        return result;
    }
}
