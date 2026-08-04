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
 * File-system operations common to GC log sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * The storage format of a GC log source.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    /**
     * Discover the source format from the file system and its magic bytes.
     *
     * @param path source to inspect
     * @return discovered source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
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
     * Return the physical size of a source in bytes.
     *
     * @param path source to size
     * @return size reported by the file system
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.size(path);
    }

    /**
     * Open the lines in a plain, ZIP, or GZIP source. For ZIP sources, the first
     * non-directory entry is opened. Closing the returned stream closes all of
     * its underlying file and decompression streams.
     *
     * @param path source to open
     * @return lazily read lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path path) throws IOException {
        Format format = discover(path);
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static Stream<String> openZip(Path path) throws IOException {
        InputStream fileInput = Files.newInputStream(path);
        try {
            ZipInputStream zipInput = new ZipInputStream(fileInput);
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());

            if (entry == null) {
                zipInput.close();
                return Stream.empty();
            }
            return lines(zipInput);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(fileInput, exception);
            throw exception;
        }
    }

    private static Stream<String> openGZip(Path path) throws IOException {
        InputStream fileInput = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(fileInput));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(fileInput, exception);
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
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
