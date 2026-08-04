// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogs;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
 * Common operations for garbage collection log sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * The supported kinds of log source.
     */
    public enum Type {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /**
     * Discover a source type from its filesystem type and leading bytes.
     *
     * @param source source to inspect
     * @return the source type
     * @throws IOException if the source cannot be inspected
     */
    public static Type discover(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Type.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(source)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return Type.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return Type.ZIP;
            }
            return Type.PLAIN_TEXT;
        }
    }

    /**
     * Return the stored size of a source in bytes.
     *
     * @param source source to size
     * @return stored byte count
     * @throws IOException if the source cannot be inspected
     */
    public static long size(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        return Files.size(source);
    }

    /**
     * Discover the immediate filesystem entries in a directory.
     *
     * @param directory directory to inspect
     * @return entries in encounter order
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> discoverFiles(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        try (Stream<Path> files = Files.list(directory)) {
            return files.collect(Collectors.toList());
        }
    }

    /**
     * Open a source as UTF-8 lines. For ZIP files, the first non-directory
     * entry is opened.
     *
     * @param source source to open
     * @return a closeable stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path source) throws IOException {
        Type type = discover(source);
        switch (type) {
            case PLAIN_TEXT:
                return Files.lines(source, StandardCharsets.UTF_8);
            case ZIP:
                return lines(firstZipEntry(source));
            case GZIP:
                return lines(gzip(source));
            case DIRECTORY:
                throw new IOException("Unable to open a directory as a log stream: " + source);
            default:
                throw new IOException("Unsupported log source: " + source);
        }
    }

    /**
     * Open a named ZIP entry as UTF-8 lines.
     *
     * @param source ZIP source
     * @param entryName entry to open
     * @return a closeable stream of lines
     * @throws IOException if the entry cannot be opened
     */
    public static Stream<String> open(Path source, String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        ZipInputStream zip = new ZipInputStream(Files.newInputStream(source));
        boolean found = false;
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return lines(zip);
        } finally {
            if (!found) {
                zip.close();
            }
        }
    }

    /**
     * Discover the non-directory entries in a ZIP source.
     *
     * @param source ZIP source
     * @return entry names in archive order
     * @throws IOException if the ZIP source cannot be read
     */
    public static List<String> entries(Path source) throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            return zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private static InputStream firstZipEntry(Path source) throws IOException {
        ZipInputStream zip = new ZipInputStream(Files.newInputStream(source));
        boolean entryFound = false;
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entryFound = true;
                    break;
                }
            }
            if (entryFound) {
                return zip;
            }
            return new ByteArrayInputStream(new byte[0]);
        } finally {
            if (!entryFound) {
                zip.close();
            }
        }
    }

    private static InputStream gzip(Path source) throws IOException {
        InputStream input = Files.newInputStream(source);
        try {
            return new GZIPInputStream(input);
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
