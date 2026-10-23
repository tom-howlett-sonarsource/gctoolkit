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
 * ZIP and GZIP sources are transparently decompressed; for a ZIP source the first
 * non-directory entry is the file of interest.
 */
public final class LogSources {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Discover the format of a log source by inspecting its leading magic bytes.
     * Sources that cannot be read, or that are not recognised as compressed, are treated as plain text.
     * @param path The path to the log source.
     * @return The format of the log source.
     */
    public static LogSourceFormat discoverFormat(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path))
            return LogSourceFormat.DIRECTORY;
        int magicByte1 = -1;
        int magicByte2 = -1;
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            magicByte1 = magicByteReader.read();
            magicByte2 = magicByteReader.read();
        } catch (IOException ignored) {
            // unreadable sources fall through to plain text; opening them will report the failure
        }
        if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2)
            return LogSourceFormat.GZIP;
        if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2)
            return LogSourceFormat.ZIP;
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * Return the size, in bytes, of the log source as stored on disk (i.e., compressed size for ZIP and GZIP).
     * @param path The path to the log source.
     * @return The size of the source in bytes.
     * @throws IOException if the size cannot be determined.
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open a decompressed input stream over the log source.
     * @param path The path to the log source.
     * @return An input stream of the (decompressed) log data. The caller is responsible for closing it.
     * @throws IOException if the source is a directory or cannot be read. A ZIP without file entries yields an empty stream.
     */
    public static InputStream open(Path path) throws IOException {
        LogSourceFormat format = discoverFormat(path);
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
     * Stream the lines of the log source. Closing the stream closes the underlying source.
     * @param path The path to the log source.
     * @return A stream of lines from the (decompressed) log source.
     * @throws IOException if the source cannot be opened.
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
        InputStream raw = Files.newInputStream(path);
        try {
            return new GZIPInputStream(raw);
        } catch (IOException | RuntimeException e) {
            raw.close();
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
            return zipStream;
        } catch (IOException | RuntimeException e) {
            zipStream.close();
            throw e;
        }
    }
}
