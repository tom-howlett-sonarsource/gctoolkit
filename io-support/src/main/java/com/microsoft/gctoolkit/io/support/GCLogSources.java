// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared production IO helpers for GC log sources: source discovery via magic
 * bytes, and opening plain, ZIP, and GZIP log streams.
 *
 * <p>Behaviour matches the previous per-module implementations exactly so
 * existing callers do not observe any change.</p>
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;
    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private static final int MAGIC_LENGTH = 2;

    private GCLogSources() {
        // utility class
    }

    /**
     * Detect the format of the source at {@code path} by inspecting its magic bytes.
     * Directories are reported as {@link LogFormat#DIRECTORY} without any I/O beyond
     * the directory check.
     *
     * @param path the file or directory to inspect
     * @return the detected {@link LogFormat}; {@link LogFormat#UNKNOWN} when the path
     *         does not exist or is otherwise unreadable
     */
    public static LogFormat detectFormat(Path path) {
        if (path == null) {
            return LogFormat.UNKNOWN;
        }
        if (path.toFile().isDirectory()) {
            return LogFormat.DIRECTORY;
        }
        int[] magic = readMagicBytes(path);
        if (magic.length < MAGIC_LENGTH) {
            return LogFormat.UNKNOWN;
        }
        if (magic[0] == GZIP_MAGIC1 && magic[1] == GZIP_MAGIC2) {
            return LogFormat.GZIP;
        }
        if (magic[0] == ZIP_MAGIC1 && magic[1] == ZIP_MAGIC2) {
            return LogFormat.ZIP;
        }
        return LogFormat.PLAINTEXT;
    }

    /**
     * Read up to the first two magic bytes from {@code path}. Missing bytes at
     * end-of-file are omitted. Returns an empty array on I/O failure and logs a warning.
     *
     * @param path the file to read
     * @return the leading bytes as unsigned integer values
     */
    public static int[] readMagicBytes(Path path) {
        int[] buffer = new int[MAGIC_LENGTH];
        int count = 0;
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            for (int i = 0; i < MAGIC_LENGTH; i++) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                buffer[i] = b;
                count++;
            }
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return new int[0];
        }
        if (count == MAGIC_LENGTH) {
            return buffer;
        }
        int[] trimmed = new int[count];
        System.arraycopy(buffer, 0, trimmed, 0, count);
        return trimmed;
    }

    /**
     * Open a plain text log file as a stream of lines. Closing the returned
     * stream releases the underlying file.
     *
     * @param path the log file
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openPlainStream(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-compressed log file as a stream of lines. The first non-directory
     * entry is read. Closing the returned stream closes the underlying archive.
     *
     * @param path the ZIP archive
     * @return a stream of lines from the first log entry
     * @throws IOException if the archive cannot be opened
     */
    public static Stream<String> openZipStream(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
        } catch (IOException ioe) {
            zipStream.close();
            throw ioe;
        }
        return linesClosingReader(zipStream);
    }

    /**
     * Open a GZIP-compressed log file as a stream of lines. Closing the returned
     * stream closes the underlying archive.
     *
     * @param path the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> openGZipStream(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return linesClosingReader(gzipStream);
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> linesClosingReader(java.io.InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(in)));
        return reader.lines().onClose(closeQuietly(reader));
    }

    /**
     * Open a log source, choosing the appropriate stream implementation from
     * {@code format}.
     *
     * @param path the log source
     * @param format the format previously detected for {@code path}
     * @return a stream of lines
     * @throws IOException if the source cannot be opened, or {@code format} is
     *         not a readable log format
     */
    public static Stream<String> openStream(Path path, LogFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlainStream(path);
            case ZIP:
                return openZipStream(path);
            case GZIP:
                return openGZipStream(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Convenience overload that discovers the format first and then opens the stream.
     *
     * @param path the log source
     * @return a stream of lines
     * @throws IOException if the source cannot be opened or its format is unknown
     */
    public static Stream<String> openStream(Path path) throws IOException {
        return openStream(path, detectFormat(path));
    }

    private static Runnable closeQuietly(BufferedReader reader) {
        return () -> {
            try {
                reader.close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        };
    }
}
