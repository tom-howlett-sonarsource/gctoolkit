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
 * Discovers and opens GC log sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private GCLogSource() {
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    /**
     * Detects a source format from its type and leading bytes.
     *
     * @param source source path
     * @return detected source format
     * @throws IOException when the source cannot be inspected
     */
    public static Format discover(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(source))) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1 && secondByte == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1 && secondByte == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the physical size of a source in bytes.
     *
     * @param source source path
     * @return source size in bytes
     * @throws IOException when the size cannot be read
     */
    public static long sizeInBytes(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        return Files.size(source);
    }

    /**
     * Opens the source as a lazily read stream of lines. Closing the returned
     * stream closes the underlying file or compressed stream.
     *
     * @param source source path
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
            default:
                throw new IOException("Unable to read " + source);
        }
    }

    private static Stream<String> openZip(Path source) throws IOException {
        ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(source));
        try {
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
            closeAfterFailedOpen(zipInput, exception);
            throw exception;
        }
    }

    private static Stream<String> openGzip(Path source) throws IOException {
        InputStream sourceInput = Files.newInputStream(source);
        try {
            return lines(new GZIPInputStream(sourceInput));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailedOpen(sourceInput, exception);
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void closeAfterFailedOpen(InputStream input, Exception exception) {
        try {
            input.close();
        } catch (IOException closeException) {
            exception.addSuppressed(closeException);
        }
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
