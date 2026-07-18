// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A file-system source for a GC log.
 */
public final class LogFileSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    /** Source path. */
    private final Path path;
    /** Discovered source format. */
    private final LogFileFormat format;

    private LogFileSource(final Path sourcePath,
                          final LogFileFormat sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discovers the source format from the path and its leading bytes.
     *
     * @param path source path
     * @return discovered source
     * @throws IOException if the path cannot be inspected
     */
    public static LogFileSource from(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogFileSource(path, LogFileFormat.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1
                    && secondByte == GZIP_MAGIC_BYTE_2) {
                return new LogFileSource(path, LogFileFormat.GZIP);
            }
            if (firstByte == ZIP_MAGIC_BYTE_1
                    && secondByte == ZIP_MAGIC_BYTE_2) {
                return new LogFileSource(path, LogFileFormat.ZIP);
            }
            return new LogFileSource(path, LogFileFormat.PLAIN_TEXT);
        }
    }

    /**
     * Returns the source path.
     *
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the discovered source format.
     *
     * @return source format
     */
    public LogFileFormat getFormat() {
        return format;
    }

    /**
     * Returns the physical size of the source file.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long getByteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as a stream of lines. For ZIP sources, only the first
     * non-directory entry is opened.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return compressedLines(
                        new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream zipInput =
                new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return compressedLines(zipInput);
        } catch (IOException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private static Stream<String> compressedLines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input),
                        StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
