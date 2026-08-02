// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
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
 * Discovers and opens a GC log source without exposing compression-specific
 * IO to callers.
 */
public final class LogSource {
    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_1 = 0x1F;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_2 = 0x8B;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_2 = 0x4B;

    /** Source path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private LogSource(final Path sourcePath) {
        this.path = Objects.requireNonNull(sourcePath);
        this.format = discoverFormat(sourcePath);
    }

    /**
     * Creates a log source for a path.
     * @param path source path
     * @return log source
     */
    public static LogSource of(final Path path) {
        return new LogSource(path);
    }

    /**
     * Discovers sources in a directory.
     * @param directory directory to list
     * @return lazily populated source paths
     * @throws IOException when the directory cannot be listed
     */
    public static Stream<Path> discover(final Path directory)
            throws IOException {
        return Files.list(directory);
    }

    /**
     * Returns the source path.
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the physical source size.
     * @return source size in bytes
     * @throws IOException when the size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Tests whether the source is a directory.
     * @return true for a directory
     */
    public boolean isDirectory() {
        return format == Format.DIRECTORY;
    }

    /**
     * Tests whether the source is plain text.
     * @return true for plain text
     */
    public boolean isPlainText() {
        return format == Format.PLAIN_TEXT;
    }

    /**
     * Tests whether the source is ZIP compressed.
     * @return true for ZIP compression
     */
    public boolean isZip() {
        return format == Format.ZIP;
    }

    /**
     * Tests whether the source is GZIP compressed.
     * @return true for GZIP compression
     */
    public boolean isGZip() {
        return format == Format.GZIP;
    }

    /**
     * Opens lines from plain text, GZIP, or the first file in a ZIP source.
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (isPlainText()) {
            return Files.lines(path);
        }
        if (isZip()) {
            return firstZipEntryLines();
        }
        if (isGZip()) {
            return readerLines(
                    new GZIPInputStream(Files.newInputStream(path)));
        }
        throw new IOException("Unable to read " + path);
    }

    /**
     * Opens a named file in a ZIP source.
     * @param entryName ZIP entry name
     * @return entry lines
     * @throws IOException when the entry cannot be opened
     */
    public Stream<String> lines(final String entryName) throws IOException {
        if (!isZip()) {
            throw new IOException(path + " is not a ZIP source");
        }

        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to find ZIP entry " + entryName
                        + " in " + path);
            }
            return readerLines(zipFile.getInputStream(entry), zipFile);
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    /**
     * Lists non-directory files in a ZIP source.
     * @return ZIP entry names
     * @throws IOException when the source cannot be opened
     */
    public List<String> zipEntries() throws IOException {
        if (!isZip()) {
            return List.of();
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return nonDirectoryEntries(zipFile).stream()
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Tests the first two bytes of a source.
     * @param path source path
     * @param first expected first byte
     * @param second expected second byte
     * @return true when both bytes match
     */
    public static boolean hasMagic(final Path path, final int first,
                                   final int second) {
        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            return input.read() == first && input.read() == second;
        } catch (IOException exception) {
            return false;
        }
    }

    private Stream<String> firstZipEntryLines() throws IOException {
        ZipInputStream zipInput =
                new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return readerLines(zipInput);
        } catch (IOException | RuntimeException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private static Stream<String> readerLines(
            final InputStream input,
            final Closeable... additionalResources) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        Stream<String> lines = reader.lines().onClose(() -> close(reader));
        for (Closeable resource : additionalResources) {
            lines = lines.onClose(() -> close(resource));
        }
        return lines;
    }

    private static void close(final Closeable resource) {
        try {
            resource.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<ZipEntry> nonDirectoryEntries(
            final ZipFile zipFile) {
        return zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .collect(Collectors.toList());
    }

    private static Format discoverFormat(final Path path) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC_1, GZIP_MAGIC_2)) {
            return Format.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC_1, ZIP_MAGIC_2)) {
            return Format.ZIP;
        }
        return Format.PLAIN_TEXT;
    }

    /** Supported source formats. */
    private enum Format {
        /** Directory source. */
        DIRECTORY,
        /** GZIP source. */
        GZIP,
        /** Plain text source. */
        PLAIN_TEXT,
        /** ZIP source. */
        ZIP
    }
}
