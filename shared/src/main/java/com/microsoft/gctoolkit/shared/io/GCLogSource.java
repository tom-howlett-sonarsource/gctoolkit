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
 * File-system and stream operations shared by GC log data sources.
 */
public final class GCLogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        /** A directory containing log sources. */
        DIRECTORY,
        /** A GZIP-compressed source. */
        GZIP,
        /** A ZIP archive source. */
        ZIP,
        /** An uncompressed text source. */
        PLAIN_TEXT
    }

    /**
     * Detect the source format from the file type and leading magic bytes.
     * Unreadable, non-directory paths retain the historical plain-text
     * classification.
     *
     * @param path source path
     * @return detected format
     */
    public static Format format(final Path path) {
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
        } catch (IOException ignored) {
            // LogFileMetadata historically classified unreadable paths as
            // plain text.
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Test the first two bytes of a source.
     *
     * @param path source path
     * @param first expected first unsigned byte
     * @param second expected second unsigned byte
     * @return whether both bytes match
     */
    public static boolean hasMagic(final Path path, final int first,
                                   final int second) {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == first && input.read() == second;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Return the on-disk size of a source in bytes.
     *
     * @param path source path
     * @return byte size
     * @throws IOException if the size cannot be read
     */
    public static long size(final Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Discover paths next to a source, or within it when the source is a
     * directory. The returned stream must be closed by its caller.
     *
     * @param source file or directory source
     * @return paths in the source directory
     * @throws IOException if the directory cannot be listed
     */
    public static Stream<Path> discover(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        Path directory;
        if (Files.isDirectory(source)) {
            directory = source;
        } else {
            directory = source.getParent();
            if (directory == null) {
                directory = Path.of(".");
            }
        }
        return Files.list(directory);
    }

    /**
     * Discover all non-directory entries in a ZIP source.
     *
     * @param path ZIP source
     * @return entry names in archive order
     * @throws IOException if the ZIP cannot be read
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
     * Open a plain, ZIP, or GZIP source as a stream of UTF-8 lines. For a ZIP,
     * the first non-directory entry is used.
     *
     * @param path source path
     * @return line stream; callers must close it
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        Format format = format(path);
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
     * Open a plain-text source as UTF-8 lines.
     *
     * @param path source path
     * @return line stream; callers must close it
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openPlain(final Path path) throws IOException {
        return Files.lines(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open the first non-directory entry in a ZIP as UTF-8 lines.
     *
     * @param path ZIP source
     * @return line stream; callers must close it
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openZip(final Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path");
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    /**
     * Open a named ZIP entry as UTF-8 lines.
     *
     * @param path ZIP source
     * @param entryName entry name
     * @return line stream; callers must close it
     * @throws IOException if the source or entry cannot be opened
     */
    public static Stream<String> openZipEntry(final Path path,
                                              final String entryName)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryName, "entryName");
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return lines(zipFile.getInputStream(entry))
                    .onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipFile, exception);
            throw exception;
        }
    }

    /**
     * Open a GZIP source as UTF-8 lines.
     *
     * @param path GZIP source
     * @return line stream; callers must close it
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openGzip(final Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path");
        InputStream input = Files.newInputStream(source);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void closeAfterFailure(final Closeable closeable,
                                          final Exception original) {
        try {
            closeable.close();
        } catch (IOException closeException) {
            original.addSuppressed(closeException);
        }
    }
}
