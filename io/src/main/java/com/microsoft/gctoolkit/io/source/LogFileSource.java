// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
 * A filesystem source containing a plain, ZIP, or GZIP GC log.
 */
public final class LogFileSource {

    private static final int GZIP_MAGIC_1 = 0x1F;
    private static final int GZIP_MAGIC_2 = 0x8B;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4B;

    private final Path path;
    private final LogFileFormat format;

    private LogFileSource(Path path) {
        this.path = Objects.requireNonNull(path);
        this.format = discover(path);
    }

    /**
     * Discovers a log source from its filesystem path and content.
     *
     * @param path path to the source
     * @return the discovered source
     */
    public static LogFileSource from(Path path) {
        return new LogFileSource(path);
    }

    /**
     * Returns the discovered source format.
     *
     * @return source format
     */
    public LogFileFormat format() {
        return format;
    }

    /**
     * Returns the source size in bytes on the filesystem.
     *
     * @return source byte size
     * @throws IOException when the source size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as a stream of lines. ZIP sources expose the first non-directory entry.
     * Closing the returned stream closes all underlying filesystem and compression resources.
     *
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return compressedLines(new GZIPInputStream(Files.newInputStream(path)));
            case DIRECTORY:
                throw new IOException("Unable to read directory as a log source: " + path);
            default:
                throw new IOException("Unable to read log source: " + path);
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return compressedLines(input);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> compressedLines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static LogFileFormat discover(Path path) {
        if (Files.isDirectory(path)) {
            return LogFileFormat.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_1 && secondByte == GZIP_MAGIC_2) {
                return LogFileFormat.GZIP;
            }
            if (firstByte == ZIP_MAGIC_1 && secondByte == ZIP_MAGIC_2) {
                return LogFileFormat.ZIP;
            }
        } catch (IOException ignored) {
            return LogFileFormat.PLAIN_TEXT;
        }
        return LogFileFormat.PLAIN_TEXT;
    }
}
