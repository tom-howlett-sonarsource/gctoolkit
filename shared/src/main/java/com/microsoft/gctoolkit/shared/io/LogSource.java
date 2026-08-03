// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A filesystem source for GC log text. The source format is discovered from
 * the file's magic bytes rather than its name.
 */
public final class LogSource {

    /** First magic byte for GZIP sources. */
    private static final int GZIP_MAGIC_1 = 0x1F;
    /** Second magic byte for GZIP sources. */
    private static final int GZIP_MAGIC_2 = 0x8B;
    /** First magic byte for ZIP sources. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second magic byte for ZIP sources. */
    private static final int ZIP_MAGIC_2 = 0x4B;

    /** Path to the source. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private LogSource(final Path sourcePath) {
        this.path = Objects.requireNonNull(sourcePath, "path");
        this.format = discoverFormat(sourcePath);
    }

    /**
     * Discover a log source at {@code path}.
     *
     * @param path source path
     * @return the discovered source
     */
    public static LogSource from(final Path path) {
        return new LogSource(path);
    }

    /**
     * @return the source path
     */
    public Path path() {
        return path;
    }

    /**
     * @return the source format
     */
    public Format format() {
        return format;
    }

    /**
     * Return the physical size of this source. Directory sizes are the sum of
     * their regular files.
     *
     * @return size in bytes
     * @throws IOException if the source cannot be sized
     */
    public long byteSize() throws IOException {
        if (format != Format.DIRECTORY) {
            return Files.size(path);
        }
        try (Stream<Path> files = Files.walk(path)) {
            try {
                return files.filter(Files::isRegularFile)
                        .mapToLong(LogSource::sizeUnchecked)
                        .sum();
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    /**
     * Discover immediate filesystem sources. A regular source returns itself;
     * a directory returns its immediate children.
     *
     * @return paths belonging to this source
     * @throws IOException if a directory cannot be listed
     */
    public Stream<Path> paths() throws IOException {
        return format == Format.DIRECTORY ? Files.list(path) : Stream.of(path);
    }

    /**
     * Discover non-directory entries in a ZIP source in archive order.
     *
     * @return ZIP entry names
     * @throws IOException if the source is not a ZIP or cannot be read
     */
    public List<String> zipEntryNames() throws IOException {
        requireZip();
        List<String> names = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(
                Files.newInputStream(path))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
    }

    /**
     * Open this source as a stream of lines. For ZIP files, the first
     * non-directory entry is opened.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return firstZipEntryLines();
            case GZIP:
                return readerLines(new BufferedReader(new InputStreamReader(
                        new BufferedInputStream(new GZIPInputStream(
                                Files.newInputStream(path))))));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a named entry in a ZIP source as a stream of lines.
     *
     * @param entryName entry to open
     * @return entry lines
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> lines(final String entryName) throws IOException {
        requireZip();
        Objects.requireNonNull(entryName, "entryName");
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    return readerLines(new BufferedReader(new InputStreamReader(
                            new BufferedInputStream(input))));
                }
            }
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
        input.close();
        throw new IOException("ZIP entry not found: " + entryName);
    }

    private Stream<String> firstZipEntryLines() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    return readerLines(new BufferedReader(new InputStreamReader(
                            new BufferedInputStream(input))));
                }
            }
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
        input.close();
        throw new IOException("ZIP contains no file entries: " + path);
    }

    private void requireZip() throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Not a ZIP source: " + path);
        }
    }

    private static Stream<String> readerLines(final BufferedReader reader) {
        return reader.lines().onClose(() -> closeUnchecked(reader));
    }

    private static void closeUnchecked(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static long sizeUnchecked(final Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Format discoverFormat(final Path path) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (BufferedInputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
        } catch (IOException ignored) {
            // Preserve the existing behavior: unreadable paths are treated as
            // plain text and fail only when a caller attempts to open them.
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Supported filesystem source formats.
     */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed text. */
        GZIP,
        /** Filesystem directory. */
        DIRECTORY
    }
}
