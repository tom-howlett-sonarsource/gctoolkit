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
 * Utilities for discovering and reading a GC log source.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Describes the storage format of a GC log source.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    /**
     * Discovers the source format from its file type and magic bytes.
     *
     * @param source source to inspect
     * @return discovered source format
     * @throws IOException when the source cannot be inspected
     */
    public static Format discover(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }

        try (var input = Files.newInputStream(source)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the physical size of the source in bytes.
     *
     * @param source source to size
     * @return source size in bytes
     * @throws IOException when the source size cannot be read
     */
    public static long byteSize(Path source) throws IOException {
        return Files.size(Objects.requireNonNull(source, "source"));
    }

    /**
     * Opens the source as a stream of lines. ZIP sources read the first non-directory entry.
     * Closing the returned stream closes all underlying resources.
     *
     * @param source source to open
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public static Stream<String> open(Path source) throws IOException {
        Format format = discover(source);
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(source);
            case ZIP:
                return openZip(source);
            case GZIP:
                return openGzip(source);
            case DIRECTORY:
                throw new IOException("Unable to read directory " + source);
            default:
                throw new IOException("Unable to read " + source);
        }
    }

    private static Stream<String> openZip(Path source) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
                input.closeEntry();
            }
            if (entry == null) {
                throw new IOException("ZIP source contains no log file: " + source);
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private static Stream<String> openGzip(Path source) throws IOException {
        InputStream input = Files.newInputStream(source);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
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

    private static void closeAfterFailure(InputStream input, Exception failure) {
        try {
            input.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
