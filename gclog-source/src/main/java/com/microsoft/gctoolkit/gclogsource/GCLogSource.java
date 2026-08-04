// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

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
 * Discovers and opens a plain, ZIP, or GZIP GC log source.
 * ZIP sources expose the first non-directory entry, matching the single-log
 * behavior used by GCToolKit.
 */
public final class GCLogSource {

    /** First byte in the GZIP signature. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    /** Second byte in the GZIP signature. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    /** First byte in the ZIP signature. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second byte in the ZIP signature. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;
    /** Buffer size used when counting source bytes. */
    private static final int COPY_BUFFER_SIZE = 8192;

    /** Path backing this source. */
    private final Path path;
    /** Lazily discovered source format. */
    private Format discoveredFormat;

    /**
     * Creates a source backed by {@code path}. The format is discovered from
     * the file's magic bytes when the source is first used.
     *
     * @param sourcePath path to a GC log source
     */
    public GCLogSource(final Path sourcePath) {
        this.path = Objects.requireNonNull(sourcePath, "path");
    }

    /**
     * Returns the source format discovered from the path and its magic bytes.
     *
     * @return the source format
     * @throws IOException if the source cannot be inspected
     */
    public Format format() throws IOException {
        if (discoveredFormat == null) {
            discoveredFormat = discoverFormat();
        }
        return discoveredFormat;
    }

    /**
     * Opens the source's uncompressed bytes. For ZIP sources, the stream is
     * positioned at the first non-directory entry.
     *
     * @return an uncompressed input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format()) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openFirstZipEntry();
            case DIRECTORY:
                throw new IOException(
                        "Cannot open a directory as a GC log source: " + path);
            default:
                throw new IOException("Unsupported GC log source: " + path);
        }
    }

    /**
     * Returns the number of uncompressed bytes exposed by {@link #open()}.
     *
     * @return uncompressed source size in bytes
     * @throws IOException if the source cannot be read
     */
    public long size() throws IOException {
        try (InputStream input = open()) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long size = 0L;
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                size += bytesRead;
            }
            return size;
        }
    }

    /**
     * Streams lines from the source. Closing the returned stream closes the
     * underlying file or archive stream.
     *
     * @return a stream of source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format() == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }

        return lines(open());
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private Format discoverFormat() throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
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
            return Format.PLAIN_TEXT;
        }
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream zipInput = new ZipInputStream(
                Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return zipInput;
        } catch (IOException exception) {
            try {
                zipInput.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Unable to close GC log source", exception);
        }
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        /** Uncompressed text file. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP compressed file. */
        GZIP,
        /** File-system directory. */
        DIRECTORY
    }
}
