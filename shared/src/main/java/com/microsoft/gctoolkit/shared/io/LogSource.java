// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File-system operations shared by GC log data sources.
 */
public final class LogSource {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private LogSource() {
    }

    /**
     * Discover the format of a log source from its file type and magic bytes.
     *
     * @param path source path
     * @return discovered source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return Format.ZIP;
            }
            return Format.PLAINTEXT;
        }
    }

    /**
     * Return the physical size of a source. Directory sizes include regular files below the source.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the source cannot be sized
     */
    public static long size(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Files.size(path);
        }
        try (Stream<Path> paths = Files.walk(path)) {
            try {
                return paths.filter(Files::isRegularFile)
                        .mapToLong(LogSource::sizeUnchecked)
                        .sum();
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    /**
     * Open a log source as lines. ZIP sources expose the first non-directory entry.
     *
     * @param path source path
     * @return lines from the source
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path path) throws IOException {
        return open(path, discover(path));
    }

    private static Stream<String> open(Path path, Format format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                input.close();
                return Stream.empty();
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), Charset.defaultCharset()));
        return reader.lines().onClose(() -> close(reader));
    }

    private static long sizeUnchecked(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Supported log source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY
    }
}
