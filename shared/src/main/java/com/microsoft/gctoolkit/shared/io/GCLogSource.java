// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedReader;
import java.io.FilterInputStream;
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
 * File-system and compressed-stream operations shared by GC log data sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Discover the source format from the path and its leading bytes.
     *
     * @param path source path
     * @return discovered source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path);
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (byteSize(path) < 2) {
            return Format.PLAIN_TEXT;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Return the number of bytes occupied by a source path.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Return the uncompressed byte size recorded for a ZIP entry.
     *
     * @param path ZIP source path
     * @param entryName ZIP entry name
     * @return uncompressed entry size in bytes
     * @throws IOException if the ZIP source or entry cannot be read
     */
    public static long byteSize(Path path, String entryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = requireEntry(zipFile, entryName);
            return entry.getSize();
        }
    }

    /**
     * List non-directory entries in a ZIP source.
     *
     * @param path ZIP source path
     * @return entry names in archive order
     * @throws IOException if the ZIP source cannot be read
     */
    public static List<String> entries(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open the content stream for a plain, ZIP, or GZIP source. For ZIP sources,
     * the first non-directory entry is selected.
     *
     * @param path source path
     * @return content input stream
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAIN_TEXT) {
            return Files.newInputStream(path);
        }
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return input;
        }
        throw new IOException("Unable to read " + path);
    }

    /**
     * Open a named entry from a ZIP source.
     *
     * @param path ZIP source path
     * @param entryName ZIP entry name
     * @return entry input stream
     * @throws IOException if the ZIP source or entry cannot be opened
     */
    public static InputStream open(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = requireEntry(zipFile, entryName);
            InputStream input = zipFile.getInputStream(entry);
            return new ZipEntryInputStream(input, zipFile);
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    /**
     * Stream lines from a plain, ZIP, or GZIP source.
     *
     * @param path source path
     * @return lazy line stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        if (format == Format.ZIP || format == Format.GZIP) {
            return lines(open(path));
        }
        throw new IOException("Unable to read " + path);
    }

    /**
     * Stream lines from a named ZIP entry.
     *
     * @param path ZIP source path
     * @param entryName ZIP entry name
     * @return lazy line stream
     * @throws IOException if the ZIP source or entry cannot be opened
     */
    public static Stream<String> lines(Path path, String entryName) throws IOException {
        return lines(open(path, entryName));
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static ZipEntry requireEntry(ZipFile zipFile, String entryName) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Unable to read ZIP entry " + entryName);
        }
        return entry;
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    private static final class ZipEntryInputStream extends FilterInputStream {
        private final ZipFile zipFile;

        private ZipEntryInputStream(InputStream input, ZipFile zipFile) {
            super(input);
            this.zipFile = zipFile;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                zipFile.close();
            }
        }
    }
}
