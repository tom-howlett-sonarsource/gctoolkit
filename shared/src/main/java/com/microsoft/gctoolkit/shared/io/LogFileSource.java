// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
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
import java.util.zip.ZipFile;

/**
 * A readable logical source within a GC log file.
 */
public final class LogFileSource {

    /** Number of bytes read at a time while calculating size. */
    private static final int BUFFER_SIZE = 8192;

    /** File-system path containing this source. */
    private final Path path;
    /** Format of the containing path. */
    private final LogFileFormat format;
    /** ZIP entry name, or {@code null} for non-ZIP sources. */
    private final String entryName;
    /** Known uncompressed size, or {@code -1} when it must be calculated. */
    private final long knownSize;

    LogFileSource(final Path sourcePath, final LogFileFormat sourceFormat,
                  final String sourceEntryName, final long sourceSize) {
        this.path = Objects.requireNonNull(sourcePath);
        this.format = Objects.requireNonNull(sourceFormat);
        this.entryName = sourceEntryName;
        this.knownSize = sourceSize;
    }

    /**
     * Returns the path containing this source.
     * @return containing path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the source name.
     * @return ZIP entry name or file name
     */
    public String name() {
        Path fileName = path.getFileName();
        return entryName == null
                ? fileName == null ? path.toString() : fileName.toString()
                : entryName;
    }

    /**
     * Returns the source format.
     * @return source format
     */
    public LogFileFormat format() {
        return format;
    }

    /**
     * Returns the uncompressed source size in bytes.
     * @return uncompressed byte size
     * @throws UncheckedIOException when the source cannot be read
     */
    public long size() {
        if (knownSize >= 0) {
            return knownSize;
        }
        try (InputStream input = open()) {
            long size = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
            }
            return size;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Opens the source for reading.
     * @return input stream for the uncompressed source
     * @throws IOException when the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            case GZIP:
                return new GZIPInputStream(
                        new BufferedInputStream(Files.newInputStream(path)));
            case ZIP:
                return openZipEntry();
            default:
                throw new IOException("Unable to open " + path);
        }
    }

    /**
     * Opens the source as UTF-8 lines.
     * @return stream of source lines
     * @throws IOException when the source cannot be opened
     */
    @SuppressFBWarnings(
            value = "OS_OPEN_STREAM",
            justification = "The returned stream closes the reader on close")
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(open(), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openZipEntry() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException(
                    "Unable to find " + entryName + " in " + path);
        }
        InputStream input = new BufferedInputStream(
                zipFile.getInputStream(entry));
        return new FilterInputStream(input) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zipFile.close();
                }
            }
        };
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
