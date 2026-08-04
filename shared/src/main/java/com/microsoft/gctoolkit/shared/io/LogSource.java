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
 * Discovers and opens a plain, ZIP, or GZIP log source.
 */
public final class LogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_FIRST_BYTE = 0x1F;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_SECOND_BYTE = 0x8B;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_FIRST_BYTE = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_SECOND_BYTE = 0x4B;

    /** Path to the log source. */
    private final Path path;
    /** Lazily detected source format. */
    private Format discoveredFormat;

    /**
     * Creates a source rooted at {@code sourcePath}.
     *
     * @param sourcePath path to the source
     */
    public LogSource(final Path sourcePath) {
        this.path = Objects.requireNonNull(sourcePath, "sourcePath");
    }

    /**
     * Returns the source path.
     *
     * @return the source path
     */
    public Path path() {
        return path;
    }

    /**
     * Detects the source format from its leading bytes.
     *
     * @return the detected source format
     * @throws IOException if the source cannot be inspected
     */
    public Format format() throws IOException {
        if (discoveredFormat == null) {
            discoveredFormat = discoverFormat();
        }
        return discoveredFormat;
    }

    /**
     * Returns the source size on disk in bytes.
     *
     * @return the source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as a stream of lines. For ZIP sources, the first
     * non-directory entry is opened.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format()) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZipLines();
            case GZIP:
                return openCompressedLines(
                        new GZIPInputStream(Files.newInputStream(path)));
            case DIRECTORY:
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Format discoverFormat() throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_FIRST_BYTE
                    && secondByte == GZIP_MAGIC_SECOND_BYTE) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_FIRST_BYTE
                    && secondByte == ZIP_MAGIC_SECOND_BYTE) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    private Stream<String> openZipLines() throws IOException {
        ZipInputStream zipInput =
                new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return openCompressedLines(zipInput);
        } catch (IOException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private Stream<String> openCompressedLines(final InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), Charset.defaultCharset()));
        return reader.lines().onClose(() -> close(reader));
    }

    private void close(final BufferedReader reader) {
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
        /** A plain-text source. */
        PLAIN_TEXT,
        /** A ZIP-compressed source. */
        ZIP,
        /** A GZIP-compressed source. */
        GZIP,
        /** A directory source. */
        DIRECTORY
    }
}
