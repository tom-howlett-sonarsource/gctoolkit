// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

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
 * Shared IO utilities used by both the API and parser modules to discover
 * GC log sources, size them, and open plain, ZIP, or GZIP log streams.
 *
 * <p>The helpers here are the single production copy of behaviour that was
 * previously duplicated in classes such as {@code SingleGCLogFile} and
 * {@code SafepointLogFile}.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;
    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private LogSources() {
        // utility class
    }

    /**
     * Detect the on-disk format of the given path by inspecting the first two
     * bytes of the file (or reporting {@link LogSourceFormat#DIRECTORY} when the
     * path is a directory).
     *
     * @param path the source path
     * @return the detected {@link LogSourceFormat}
     */
    public static LogSourceFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return LogSourceFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogSourceFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogSourceFormat.ZIP;
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} when the first two bytes at {@code path} match the
     * expected magic values.
     *
     * @param path   file to sample
     * @param first  expected first byte
     * @param second expected second byte
     * @return whether the file begins with the given magic sequence
     */
    public static boolean matchesMagic(Path path, int first, int second) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == first && b2 == second;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return false;
        }
    }

    /**
     * Return the size of the file at {@code path} in bytes, or {@code -1} if
     * the size cannot be determined.
     *
     * @param path the source path
     * @return the file size in bytes, or {@code -1} on error
     */
    public static long byteSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, ioe, () -> "Unable to determine size of " + path);
            return -1L;
        }
    }

    /**
     * Open a line stream for a plain-text log file.
     *
     * @param path the file to read
     * @return a lazily-populated stream of lines
     * @throws IOException on failure to open the file
     */
    public static Stream<String> openPlainStream(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over the first non-directory entry of a ZIP archive.
     *
     * @param path the ZIP archive
     * @return a lazily-populated stream of lines from the first entry
     * @throws IOException on failure to open the archive
     */
    @SuppressWarnings("java:S2095") // reader is closed by the caller via Stream#close
    public static Stream<String> openZipStream(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Open a line stream over a GZIP compressed log file.
     *
     * @param path the GZIP file
     * @return a lazily-populated stream of lines
     * @throws IOException on failure to open the file
     */
    @SuppressWarnings("java:S2095") // reader is closed by the caller via Stream#close
    public static Stream<String> openGzipStream(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, ioe, () -> "Failed to close reader");
        }
    }

    /**
     * Open a line stream for {@code path}, dispatching on the detected format.
     * Directories and unknown formats result in an {@link IOException}.
     *
     * @param path the source path
     * @return a lazily-populated stream of lines
     * @throws IOException if the path cannot be read as a single log source
     */
    public static Stream<String> openLines(Path path) throws IOException {
        LogSourceFormat format = detectFormat(path);
        switch (format) {
            case PLAINTEXT:
                return openPlainStream(path);
            case ZIP:
                return openZipStream(path);
            case GZIP:
                return openGzipStream(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }
}
