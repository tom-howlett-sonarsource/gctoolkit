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
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a plain, ZIP, or GZIP log file source.
 */
public final class LogFileSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    /** Source path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private LogFileSource(final Path sourcePath) {
        this.path = Objects.requireNonNull(sourcePath, "path");
        this.format = discover(sourcePath);
    }

    /**
     * Creates a source and discovers its format from the path and leading
     * bytes.
     *
     * @param path path to a log source
     * @return the discovered source
     */
    public static LogFileSource from(final Path path) {
        return new LogFileSource(path);
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
     * Returns the number of bytes occupied by the source path.
     *
     * @return source size in bytes
     * @throws IOException if the source cannot be sized
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as a lazily read stream of lines. Closing the returned
     * stream closes all underlying readers and archive streams.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> stream() throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamZipFile();
            case GZIP:
                return streamGZipFile();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Returns whether the source is a ZIP archive.
     *
     * @return {@code true} for a ZIP source
     */
    public boolean isZip() {
        return format == Format.ZIP;
    }

    /**
     * Returns whether the source is a GZIP archive.
     *
     * @return {@code true} for a GZIP source
     */
    public boolean isGZip() {
        return format == Format.GZIP;
    }

    /**
     * Returns whether the source is plain text.
     *
     * @return {@code true} for a plain-text source
     */
    public boolean isPlainText() {
        return format == Format.PLAINTEXT;
    }

    /**
     * Returns whether the source is a directory.
     *
     * @return {@code true} for a directory source
     */
    public boolean isDirectory() {
        return format == Format.DIRECTORY;
    }

    private static Format discover(final Path path) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1
                    && secondByte == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1
                    && secondByte == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
        } catch (IOException ignored) {
            // Preserve existing behavior: unreadable paths are plain text.
        }
        return Format.PLAINTEXT;
    }

    private Stream<String> streamZipFile() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(
                Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                zipStream.close();
                return Stream.empty();
            }
            return lines(zipStream);
        } catch (IOException exception) {
            closeAfterFailure(zipStream, exception);
            throw exception;
        }
    }

    private Stream<String> streamGZipFile() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, Charset.defaultCharset()));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void closeAfterFailure(final InputStream input,
                                          final IOException failure) {
        try {
            input.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private enum Format {
        /** ZIP archive. */
        ZIP,
        /** GZIP archive. */
        GZIP,
        /** Plain-text file. */
        PLAINTEXT,
        /** Directory. */
        DIRECTORY
    }
}
