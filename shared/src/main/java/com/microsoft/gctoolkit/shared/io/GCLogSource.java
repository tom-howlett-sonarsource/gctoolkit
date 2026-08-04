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
import java.nio.charset.Charset;
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
 * File-system operations shared by GC log data sources.
 */
public final class GCLogSource {

    /** First byte in the GZIP magic signature. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    /** Second byte in the GZIP magic signature. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    /** First byte in the ZIP magic signature. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second byte in the ZIP magic signature. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Discover the source format from the file system and its magic bytes.
     *
     * @param path source to inspect
     * @return the source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (var input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Return the source size in bytes.
     *
     * @param path source to size
     * @return the raw file-system size
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(final Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * List the immediate children of a source directory.
     *
     * @param directory directory to list
     * @return a closeable stream of paths
     * @throws IOException if the directory cannot be listed
     */
    public static Stream<Path> list(final Path directory) throws IOException {
        return Files.list(Objects.requireNonNull(directory, "directory"));
    }

    /**
     * Discover the file entries in a ZIP source.
     *
     * @param path ZIP source to inspect
     * @return entry names in archive order
     * @throws IOException if the ZIP source cannot be inspected
     */
    public static List<String> zipEntries(final Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path");
        try (ZipFile zipFile = new ZipFile(source.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open a plain, ZIP, or GZIP source as a stream of lines. For a ZIP source,
     * the first non-directory entry is opened.
     *
     * @param path source to open
     * @return a closeable stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        Format format = discover(path);
        switch (format) {
            case PLAIN_TEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGzip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a plain text source.
     *
     * @param path source to open
     * @return a closeable stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openPlain(final Path path) throws IOException {
        return Files.lines(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open the first non-directory entry in a ZIP source.
     *
     * @param path source to open
     * @return a closeable stream of lines
     * @throws IOException if the source cannot be opened or has no file entries
     */
    public static Stream<String> openZip(final Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path");
        ZipInputStream zipStream =
                new ZipInputStream(Files.newInputStream(source));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                throw new IOException(
                        "ZIP source contains no file entries: " + path);
            }
            return lines(zipStream);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipStream, exception);
            throw exception;
        }
    }

    /**
     * Open a named entry in a ZIP source.
     *
     * @param path ZIP source to open
     * @param entryName entry to open
     * @return a closeable stream of lines
     * @throws IOException if the source or entry cannot be opened
     */
    public static Stream<String> openZip(final Path path,
                                         final String entryName)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryName, "entryName");
        ZipInputStream zipStream =
                new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && entryName.equals(entry.getName())) {
                    return lines(zipStream);
                }
            }
            throw new IOException("ZIP entry not found: " + entryName);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipStream, exception);
            throw exception;
        }
    }

    /**
     * Open a GZIP source.
     *
     * @param path source to open
     * @return a closeable stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openGzip(final Path path) throws IOException {
        InputStream input = Files.newInputStream(
                Objects.requireNonNull(path, "path"));
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input),
                        Charset.defaultCharset()));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final Closeable... resources) {
        IOException failure = null;
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void closeAfterFailure(final Closeable resource,
                                          final Throwable failure) {
        try {
            resource.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** Plain text file. */
        PLAIN_TEXT,
        /** File-system directory. */
        DIRECTORY
    }
}
