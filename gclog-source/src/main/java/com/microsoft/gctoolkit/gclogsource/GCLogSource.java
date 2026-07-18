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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared file-system and compression utilities for GC log sources.
 */
public final class GCLogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_ONE = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4b;

    private GCLogSource() {
    }

    /**
     * Discovers the source format from the path and its magic bytes.
     *
     * @param path source path
     * @return discovered format
     * @throws IOException if the source cannot be read
     */
    public static LogSourceFormat format(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_ONE && second == GZIP_MAGIC_BYTE_TWO) {
                return LogSourceFormat.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_ONE && second == ZIP_MAGIC_BYTE_TWO) {
                return LogSourceFormat.ZIP;
            }
            return LogSourceFormat.PLAIN_TEXT;
        }
    }

    /**
     * Returns the source size in bytes. Directory sizes are the total size of
     * their directly contained regular files.
     *
     * @param path source path
     * @return size in bytes
     * @throws IOException if the source cannot be inspected
     */
    public static long size(final Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Files.size(path);
        }
        try (Stream<Path> paths = Files.list(path)) {
            long total = 0L;
            List<Path> regularFiles = paths.filter(Files::isRegularFile)
                    .collect(Collectors.toList());
            for (Path file : regularFiles) {
                total += Files.size(file);
            }
            return total;
        }
    }

    /**
     * Discovers directly contained regular files in name order.
     *
     * @param directory source directory
     * @return regular files
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> files(final Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Discovers non-directory entries in archive order.
     *
     * @param path ZIP source path
     * @return entry names
     * @throws IOException if the archive cannot be read
     */
    public static List<String> zipEntries(final Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Opens a plain, GZIP, or ZIP source. ZIP sources expose the first
     * non-directory entry.
     *
     * @param path source path
     * @return source stream
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(final Path path) throws IOException {
        LogSourceFormat sourceFormat = format(path);
        switch (sourceFormat) {
            case PLAIN_TEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            case GZIP:
                return new BufferedInputStream(
                        new GZIPInputStream(Files.newInputStream(path)));
            case ZIP:
                return openZipEntry(path, null);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens a named entry from a ZIP source.
     *
     * @param path ZIP source path
     * @param entryName entry name
     * @return entry stream
     * @throws IOException if the entry cannot be opened
     */
    public static InputStream open(final Path path, final String entryName)
            throws IOException {
        return openZipEntry(path, entryName);
    }

    /**
     * Streams lines from a plain, GZIP, or first-entry ZIP source.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(final Path path) throws IOException {
        if (format(path) == LogSourceFormat.PLAIN_TEXT) {
            return Files.lines(path);
        }
        return lineStream(open(path));
    }

    /**
     * Streams lines from a named ZIP entry.
     *
     * @param path ZIP source path
     * @param entryName entry name
     * @return entry lines
     * @throws IOException if the entry cannot be opened
     */
    public static Stream<String> lines(final Path path, final String entryName)
            throws IOException {
        return lineStream(open(path, entryName));
    }

    private static InputStream openZipEntry(final Path path,
                                            final String entryName)
            throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null) {
            boolean requestedEntry = entryName == null
                    || entryName.equals(entry.getName());
            if (!entry.isDirectory() && requestedEntry) {
                return new BufferedInputStream(input);
            }
        }
        input.close();
        String detail = entryName == null
                ? "a readable entry" : "entry " + entryName;
        throw new IOException("Unable to find " + detail + " in " + path);
    }

    private static Stream<String> lineStream(final InputStream input) {
        InputStreamReader inputReader = new InputStreamReader(
                input, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(inputReader);
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

}
