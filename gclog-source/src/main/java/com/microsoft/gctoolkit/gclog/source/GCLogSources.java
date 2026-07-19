// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.source;

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
 * Static utilities that discover GC log source formats, report byte sizes,
 * and open line streams over plain, ZIP, and GZIP encoded log files.
 * <p>
 * These primitives were previously duplicated between the API and parser
 * modules; callers should route their production IO through this class so
 * that format detection and stream construction remain consistent.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSources() {
        // utility class
    }

    /**
     * Inspect the file at the given path and classify it as a directory,
     * a GZIP file, a ZIP file, or a plaintext file. Returns {@link LogFileFormat#UNKNOWN}
     * only when {@code path} is {@code null}.
     *
     * @param path the file or directory to inspect; must not be {@code null}
     * @return the detected format
     */
    public static LogFileFormat detectFormat(Path path) {
        if (path == null) {
            return LogFileFormat.UNKNOWN;
        }
        if (path.toFile().isDirectory()) {
            return LogFileFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogFileFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogFileFormat.ZIP;
        }
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} when the first two bytes of the file at {@code path}
     * match the supplied magic values. IO errors are logged and reported as
     * a non-match.
     *
     * @param path   the file to inspect
     * @param first  the expected first byte
     * @param second the expected second byte
     * @return {@code true} if the magic bytes match
     */
    public static boolean matchesMagic(Path path, int first, int second) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            return in.read() == first && in.read() == second;
        } catch (IOException ioe) {
            LOG.log(Level.FINE, ioe, () -> "Unable to read magic bytes from " + path);
        }
        return false;
    }

    /**
     * Return the size in bytes of the source at {@code path}.
     *
     * @param path the file to size; must be a regular file
     * @return the size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a line stream over a plaintext GC log file.
     *
     * @param path the file to read
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlainLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over the first non-directory entry of a ZIP GC log file.
     * Preserves the behaviour of the previous per-module implementations, including
     * skipping leading directory entries.
     *
     * @param path the ZIP file to read
     * @return a stream of lines
     * @throws IOException if the archive cannot be opened
     */
    public static Stream<String> openZipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a line stream over a GZIP GC log file.
     *
     * @param path the GZIP file to read
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openGzipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Detect the format of {@code path} and open a line stream over it. Directories
     * and unknown formats yield an {@link IOException}.
     *
     * @param path the file to read
     * @return a stream of lines
     * @throws IOException if the file cannot be opened or the format is not streamable
     */
    public static Stream<String> openLines(Path path) throws IOException {
        LogFileFormat format = detectFormat(path);
        switch (format) {
            case PLAINTEXT:
                return openPlainLines(path);
            case ZIP:
                return openZipLines(path);
            case GZIP:
                return openGzipLines(path);
            default:
                throw new IOException("Unable to stream " + path + " (format " + format + ")");
        }
    }
}
