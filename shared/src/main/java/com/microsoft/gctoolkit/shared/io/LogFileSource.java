// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

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
 * A file-system source for a plain, ZIP, or GZIP GC log.
 */
public final class LogFileSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private final Path path;
    private final Format format;

    private LogFileSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discovers the source format from its path and magic bytes.
     *
     * @param path source path
     * @return discovered source
     */
    public static LogFileSource from(Path path) {
        Objects.requireNonNull(path, "path");
        return new LogFileSource(path, discoverFormat(path));
    }

    public Path path() {
        return path;
    }

    public long sizeInBytes() throws IOException {
        return Files.size(path);
    }

    public boolean isPlainText() {
        return format == Format.PLAIN_TEXT;
    }

    public boolean isZip() {
        return format == Format.ZIP;
    }

    public boolean isGZip() {
        return format == Format.GZIP;
    }

    public boolean isDirectory() {
        return format == Format.DIRECTORY;
    }

    /**
     * Opens a lazily read stream of log lines. For ZIP sources, the first
     * non-directory entry is used.
     *
     * @return log lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(zipStream);
        } catch (IOException | RuntimeException exception) {
            zipStream.close();
            throw exception;
        }
    }

    private Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Format discoverFormat(Path path) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1 && secondByte == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1 && secondByte == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        } catch (IOException exception) {
            return Format.PLAIN_TEXT;
        }
    }

    private enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
