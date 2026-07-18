// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

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
import java.util.zip.ZipFile;

/**
 * A readable GC log source backed by a file or an entry in a ZIP file.
 */
public final class GCLogSource {

    private static final int BUFFER_SIZE = 8192;

    private final Path path;
    private final String entryName;
    private final GCLogSources.Format format;

    GCLogSource(Path path, String entryName, GCLogSources.Format format) {
        this.path = Objects.requireNonNull(path);
        this.entryName = entryName;
        this.format = Objects.requireNonNull(format);
    }

    /**
     * Returns the path containing this source.
     *
     * @return the source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the source name. ZIP entries retain their complete entry name.
     *
     * @return the source name
     */
    public String getName() {
        return entryName == null ? path.getFileName().toString() : entryName;
    }

    /**
     * Returns the number of uncompressed bytes in this source.
     *
     * @return the uncompressed source size
     * @throws IOException if the size cannot be determined
     */
    public long size() throws IOException {
        if (entryName != null) {
            return zipEntrySize();
        }
        if (format == GCLogSources.Format.GZIP) {
            try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
                return countBytes(input);
            }
        }
        if (format == GCLogSources.Format.ZIP) {
            return GCLogSources.first(path).size();
        }
        return Files.size(path);
    }

    /**
     * Opens this source as a closeable stream of lines.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (entryName != null) {
            return zipEntryLines();
        }
        if (format == GCLogSources.Format.GZIP) {
            return readerLines(new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))),
                    Charset.defaultCharset())));
        }
        if (format == GCLogSources.Format.ZIP) {
            return GCLogSources.first(path).lines();
        }
        return Files.lines(path);
    }

    private long zipEntrySize() throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = requiredEntry(zipFile);
            long size = entry.getSize();
            if (size >= 0) {
                return size;
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                return countBytes(input);
            }
        }
    }

    private Stream<String> zipEntryLines() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = requiredEntry(zipFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(zipFile.getInputStream(entry)), Charset.defaultCharset()));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            try {
                zipFile.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private ZipEntry requiredEntry(ZipFile zipFile) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Unable to read ZIP entry " + entryName + " from " + path);
        }
        return entry;
    }

    private static Stream<String> readerLines(BufferedReader reader) {
        return reader.lines().onClose(() -> close(reader));
    }

    private static long countBytes(InputStream input) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long size = 0;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            size += bytesRead;
        }
        return size;
    }

    private static void close(AutoCloseable... resources) {
        IOException failure = null;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }
}
