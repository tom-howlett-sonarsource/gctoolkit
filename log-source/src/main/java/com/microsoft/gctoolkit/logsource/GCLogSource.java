// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedReader;
import java.io.Closeable;
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

/**
 * File-system operations shared by GC log data sources.
 */
public final class GCLogSource {

    /** First byte of the GZIP magic number. */
    private static final int GZIP_MAGIC_BYTE_ONE = 0x1f;
    /** Second byte of the GZIP magic number. */
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8b;
    /** First byte of the ZIP magic number. */
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    /** Second byte of the ZIP magic number. */
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4b;

    private GCLogSource() {
    }

    /**
     * Discover the source format from its file type and leading magic bytes.
     *
     * @param path source path
     * @return discovered source format
     * @throws IOException if the source cannot be read
     */
    public static Format discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_ONE
                    && second == GZIP_MAGIC_BYTE_TWO) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_ONE
                    && second == ZIP_MAGIC_BYTE_TWO) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Return the encoded size of a source on disk.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the source cannot be inspected
     */
    public static long sizeInBytes(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.size(path);
    }

    /**
     * Open a plain, ZIP, or GZIP source as a stream of lines. For ZIP sources,
     * the first non-directory entry is opened.
     *
     * @param path source path
     * @return lazily read lines; callers must close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        if (format == Format.ZIP) {
            return openFirstZipEntry(path);
        }
        if (format == Format.GZIP) {
            return openGzip(path);
        }
        throw new IOException(
                "Unable to read directory as a single log source: " + path);
    }

    /**
     * Open a named, non-directory entry in a ZIP source.
     *
     * @param path ZIP source path
     * @param entryName entry to open
     * @return lazily read lines; callers must close the stream
     * @throws IOException if the source or entry cannot be opened
     */
    public static Stream<String> openZipEntry(
            final Path path, final String entryName) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryName, "entryName");

        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return openZipEntry(zipFile, entry);
        } catch (IOException | RuntimeException failure) {
            zipFile.close();
            throw failure;
        }
    }

    /**
     * Return the non-directory entry names in a ZIP source, in archive order.
     *
     * @param path ZIP source path
     * @return entry names
     * @throws IOException if the source cannot be opened
     */
    public static List<String> zipEntries(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private static Stream<String> openFirstZipEntry(final Path path)
            throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.stream()
                    .filter(candidate -> !candidate.isDirectory())
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "ZIP source contains no file entries: " + path));
            return openZipEntry(zipFile, entry);
        } catch (IOException | RuntimeException failure) {
            zipFile.close();
            throw failure;
        }
    }

    private static Stream<String> openZipEntry(
            final ZipFile zipFile, final ZipEntry entry) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                zipFile.getInputStream(entry), StandardCharsets.UTF_8));
        return closeWith(reader.lines(), reader, zipFile);
    }

    private static Stream<String> openGzip(final Path path) throws IOException {
        InputStream source = Files.newInputStream(path);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(source), StandardCharsets.UTF_8));
            return closeWith(reader.lines(), reader);
        } catch (IOException | RuntimeException failure) {
            source.close();
            throw failure;
        }
    }

    private static Stream<String> closeWith(
            final Stream<String> lines, final Closeable... resources) {
        return lines.onClose(() -> {
            IOException failure = null;
            for (Closeable resource : resources) {
                try {
                    resource.close();
                } catch (IOException closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            if (failure != null) {
                throw new UncheckedIOException(failure);
            }
        });
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed content. */
        GZIP,
        /** File-system directory. */
        DIRECTORY
    }
}
