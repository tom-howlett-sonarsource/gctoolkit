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
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering the format of, sizing, and opening GC log sources.
 * Compressed sources are transparently decompressed. For ZIP sources, the first
 * non-directory entry is the file of interest.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Discover the format of a log source by inspecting its magic bytes.
     * Unreadable files are treated as plain text.
     * @param path The path to the log source.
     * @return The format of the log source.
     */
    public static LogSourceFormat discover(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path))
            return LogSourceFormat.DIRECTORY;
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2)
                return LogSourceFormat.GZIP;
            if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2)
                return LogSourceFormat.ZIP;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * The size in bytes of a log source as stored on disk. For a directory,
     * this is the sum of the sizes of the regular files it directly contains.
     * @param path The path to the log source.
     * @return The size in bytes.
     * @throws IOException if the source cannot be read.
     */
    public static long size(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isDirectory(path))
            return Files.size(path);
        try (Stream<Path> children = Files.list(path)) {
            long total = 0;
            for (Path child : (Iterable<Path>) children.filter(Files::isRegularFile)::iterator)
                total += Files.size(child);
            return total;
        }
    }

    /**
     * Open a log source, decompressing it if necessary.
     * @param path The path to the log source.
     * @return An input stream of the (decompressed) log content. The caller must close it.
     * @throws IOException if the source cannot be opened or is a directory.
     */
    public static InputStream open(Path path) throws IOException {
        LogSourceFormat format = discover(path);
        switch (format) {
            case PLAINTEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            case GZIP:
                return openGZip(path);
            case ZIP:
                return openZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream the lines of a log source, decompressing it if necessary.
     * Closing the returned stream closes the underlying source.
     * @param path The path to the log source.
     * @return A stream of lines.
     * @throws IOException if the source cannot be opened or is a directory.
     */
    public static Stream<String> lines(Path path) throws IOException {
        if (discover(path) == LogSourceFormat.PLAINTEXT)
            return Files.lines(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(open(path)));
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
            return new BufferedInputStream(new GZIPInputStream(raw));
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
            return new BufferedInputStream(zipStream);
        } catch (IOException | RuntimeException e) {
            zipStream.close();
            throw e;
        }
    }
}
