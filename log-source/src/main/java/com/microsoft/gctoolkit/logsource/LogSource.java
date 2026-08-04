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
 * IO operations shared by consumers of GC log sources.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_1 = 0x1F;
    private static final int GZIP_MAGIC_2 = 0x8B;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4B;

    private LogSource() {
    }

    /**
     * The supported kinds of log source.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    /**
     * Discover the source format from its file type and magic bytes.
     *
     * @param source source to inspect
     * @return the discovered source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(source)) {
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
     * Return the number of bytes occupied by a source file.
     *
     * @param source source to size
     * @return size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path source) throws IOException {
        return Files.size(Objects.requireNonNull(source, "source"));
    }

    /**
     * Open the lines in a plain, ZIP, or GZIP log source. For a ZIP source, the
     * first non-directory entry is used.
     *
     * @param source source to open
     * @return a stream of source lines; closing it closes the underlying file
     * @throws IOException if the source cannot be opened
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
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
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
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }
}
