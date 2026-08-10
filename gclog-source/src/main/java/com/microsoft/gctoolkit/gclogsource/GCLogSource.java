// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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
 * Low-level operations shared by GC log data sources.
 */
public final class GCLogSource {

    /** First byte of the GZIP magic number. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second byte of the GZIP magic number. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First byte of the ZIP magic number. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second byte of the ZIP magic number. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * The supported physical forms of a GC log source.
     */
    public enum Format {
        /** A ZIP archive. */
        ZIP,
        /** A GZIP-compressed file. */
        GZIP,
        /** An uncompressed text file. */
        PLAIN_TEXT,
        /** A directory containing log files. */
        DIRECTORY
    }

    /**
     * Discover a source's format from its file type and magic bytes.
     *
     * @param path source path
     * @return discovered source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
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
     * Test the first two bytes of a source.
     *
     * @param path source path
     * @param first expected first byte
     * @param second expected second byte
     * @return {@code true} when both bytes match
     * @throws IOException if the source cannot be read
     */
    public static boolean hasMagic(final Path path, final int first,
                                   final int second) throws IOException {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == first && input.read() == second;
        }
    }

    /**
     * Return the physical size of a source in bytes.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long size(final Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open a source, transparently decompressing ZIP and GZIP input.
     * For ZIP input, the first non-directory entry is opened.
     *
     * @param path source path
     * @return source input stream
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(final Path path) throws IOException {
        return open(path, discover(path));
    }

    /**
     * Open a source using its already discovered format.
     *
     * @param path source path
     * @param format source format
     * @return source input stream
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(final Path path, final Format format)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case ZIP:
                return openFirstZipEntry(path);
            case GZIP:
                return openGzip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream the lines in a source, transparently decompressing it when needed.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(final Path path) throws IOException {
        return lines(path, discover(path));
    }

    /**
     * Stream the lines in a source using its already discovered format.
     *
     * @param path source path
     * @param format source format
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(final Path path, final Format format)
            throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        return lines(open(path, format));
    }

    /**
     * Stream the lines of a named ZIP entry.
     *
     * @param path ZIP source path
     * @param entryName ZIP entry name
     * @return entry lines
     * @throws IOException if the entry cannot be opened
     */
    public static Stream<String> lines(final Path path, final String entryName)
            throws IOException {
        return lines(openZipEntry(path, entryName));
    }

    /**
     * List the non-directory entries in a ZIP source in archive order.
     *
     * @param path ZIP source path
     * @return entry names
     * @throws IOException if the ZIP source cannot be read
     */
    public static List<String> zipEntries(final Path path) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(
                Files.newInputStream(path))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
            }
        }
        return entries;
    }

    private static InputStream openFirstZipEntry(final Path path)
            throws IOException {
        ZipInputStream input = new ZipInputStream(
                Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return input;
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static InputStream openZipEntry(final Path path,
                                            final String entryName)
            throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        ZipInputStream input = new ZipInputStream(
                Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    return input;
                }
            }
            throw new IOException("ZIP entry not found: " + entryName);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static InputStream openGzip(final Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return new GZIPInputStream(input);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input)));
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
