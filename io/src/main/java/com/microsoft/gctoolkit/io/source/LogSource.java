// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
 * A readable file or archive entry containing GC log data.
 */
public final class LogSource {

    private static final int BUFFER_SIZE = 8192;

    private final Path path;
    private final String entryName;
    private final LogSourceFormat format;
    private final long knownSize;

    LogSource(Path path, String entryName, LogSourceFormat format, long knownSize) {
        this.path = Objects.requireNonNull(path);
        this.entryName = entryName;
        this.format = Objects.requireNonNull(format);
        this.knownSize = knownSize;
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return entryName == null ? path.getFileName().toString() : entryName;
    }

    public LogSourceFormat getFormat() {
        return format;
    }

    /**
     * Returns the number of uncompressed bytes in this source.
     *
     * @return uncompressed source size
     * @throws IOException if the source cannot be read
     */
    public long size() throws IOException {
        if (format == LogSourceFormat.PLAIN_TEXT) {
            return Files.size(path);
        }
        if (format == LogSourceFormat.ZIP && knownSize >= 0) {
            return knownSize;
        }
        try (InputStream input = open()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long size = 0;
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                size += bytesRead;
            }
            return size;
        }
    }

    /**
     * Opens this source. Closing the returned stream releases all file and archive resources.
     *
     * @return source input stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openZipEntry();
            default:
                throw new IOException("Unable to open directory as a log source: " + path);
        }
    }

    /**
     * Opens this source as UTF-8 text lines.
     *
     * @return lazily read lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(open(), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openZipEntry() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().equals(entryName)) {
                    return input;
                }
            }
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
        input.close();
        throw new IOException("ZIP entry not found: " + entryName);
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
