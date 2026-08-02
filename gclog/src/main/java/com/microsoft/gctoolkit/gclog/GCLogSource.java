// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * A file-system source containing garbage collection log data.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1F;
    private static final int GZIP_MAGIC_2 = 0x8B;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4B;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover the source format from the supplied path and its magic bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the path cannot be inspected
     */
    public static GCLogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return new GCLogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return new GCLogSource(path, Format.ZIP);
            }
            return new GCLogSource(path, Format.PLAIN_TEXT);
        }
    }

    /**
     * Return the source path.
     *
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the discovered source format.
     *
     * @return source format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Return the physical size of the source in bytes.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Return the names of non-directory entries in a ZIP source.
     *
     * @return ZIP entry names
     * @throws IOException if the source cannot be read
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            return List.of();
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open the log as a stream of lines. For ZIP sources, the first non-directory
     * entry is opened.
     *
     * @return log lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> stream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return streamFirstZipEntry();
            case GZIP:
                return stream(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a named entry from a ZIP source as a stream of lines.
     *
     * @param entryName ZIP entry name
     * @return log lines
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> stream(String entryName) throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Not a ZIP source: " + path);
        }

        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException("Unable to find ZIP entry " + entryName + " in " + path);
        }

        try {
            return stream(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private Stream<String> streamFirstZipEntry() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());

        if (entry == null) {
            zipStream.close();
            throw new IOException("No log file entry found in " + path);
        }
        return stream(zipStream);
    }

    private static Stream<String> stream(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
