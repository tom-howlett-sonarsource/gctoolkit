// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering the format of, sizing and opening GC log sources.
 * A ZIP source is read from its first non-directory entry.
 */
public final class LogSources {

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Discover the format of a log source by inspecting its leading magic bytes.
     * @param path the log source
     * @return the format; unreadable or unrecognised files are treated as plain text
     */
    public static LogSourceFormat formatOf(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path))
            return LogSourceFormat.DIRECTORY;
        try (InputStream in = Files.newInputStream(path)) {
            int magic1 = in.read();
            int magic2 = in.read();
            if (magic1 == GZIP_MAGIC1 && magic2 == GZIP_MAGIC2)
                return LogSourceFormat.GZIP;
            if (magic1 == ZIP_MAGIC1 && magic2 == ZIP_MAGIC2)
                return LogSourceFormat.ZIP;
        } catch (IOException ioe) {
            // fall through: an unreadable source is reported when it is opened
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * @param path the log source
     * @return the size of the source in bytes, as stored on disk
     * @throws IOException if the size cannot be determined
     */
    public static long sizeOf(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open a decompressed byte stream on a plain, ZIP or GZIP log source.
     * @param path the log source
     * @return an input stream the caller must close
     * @throws IOException if the source is a directory, an empty ZIP, or cannot be read
     */
    public static InputStream open(Path path) throws IOException {
        LogSourceFormat format = formatOf(path);
        switch (format) {
            case PLAINTEXT:
                return Files.newInputStream(path);
            case GZIP:
                return openGZip(path);
            case ZIP:
                return openZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream the lines of a plain, ZIP or GZIP log source. Closing the stream closes the source.
     * @param path the log source
     * @return a stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path path) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(open(path))));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
    }

    private static InputStream openGZip(Path path) throws IOException {
        InputStream in = Files.newInputStream(path);
        try {
            return new GZIPInputStream(in);
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    private static InputStream openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null)
                throw new IOException("No log file entry found in " + path);
            return zipStream;
        } catch (IOException | RuntimeException e) {
            zipStream.close();
            throw e;
        }
    }
}
