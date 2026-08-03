// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a GC log source without depending on the API or parser
 * modules.
 */
public final class GCLogSource {

    /** First byte of the GZIP signature. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second byte of the GZIP signature. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First byte of the ZIP signature. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second byte of the ZIP signature. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    /** Source path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private GCLogSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discovers the format of a log source.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource discover(final Path path) throws IOException {
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
     * Lists matching children while ensuring the directory stream is closed.
     *
     * @param directory directory to inspect
     * @param filter filter applied to each child
     * @return matching paths
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> discover(final Path directory,
                                      final Predicate<Path> filter)
            throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(filter, "filter");
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(filter).collect(Collectors.toList());
        }
    }

    /**
     * @return the source path
     */
    public Path path() {
        return path;
    }

    /**
     * @return the discovered source format
     */
    public Format format() {
        return format;
    }

    /**
     * @return size of the source in bytes
     * @throws IOException if the source size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens source lines. For ZIP sources, the first non-directory entry is
     * used.
     * Closing the returned stream closes all underlying resources.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return gzipLines();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zip.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                zip.close();
                throw new IOException(
                        "ZIP source contains no log file: " + path);
            }
            return lines(zip);
        } catch (IOException | RuntimeException exception) {
            zip.close();
            throw exception;
        }
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report a checked exception.
        }
    }

    /** Supported GC log source formats. */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** Directory containing log sources. */
        DIRECTORY
    }
}
