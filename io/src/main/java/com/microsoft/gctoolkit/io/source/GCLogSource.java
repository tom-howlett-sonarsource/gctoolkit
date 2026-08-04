// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Low-level operations shared by GC log data sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private GCLogSource() {
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /**
     * Discover a source's format from the filesystem and its magic bytes.
     *
     * @param path source path
     * @return the detected source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format detectFormat(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC_BYTE_1, GZIP_MAGIC_BYTE_2)) {
            return Format.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC_BYTE_1, ZIP_MAGIC_BYTE_2)) {
            return Format.ZIP;
        }
        return Format.PLAIN_TEXT;
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
    public static boolean matchesMagic(Path path, int first, int second) throws IOException {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == first && input.read() == second;
        }
    }

    /**
     * Return the source's size on disk in bytes.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the source cannot be inspected
     */
    public static long byteSize(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.size(path);
    }

    /**
     * Discover the immediate children of a directory.
     *
     * @param directory directory to inspect
     * @return discovered source paths
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> discover(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    /**
     * Discover the non-directory entries in a ZIP source.
     *
     * @param path ZIP source path
     * @return entry names in archive order
     * @throws IOException if the ZIP source cannot be read
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
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
     * @param path source path
     * @return line stream; callers must close it
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path path) throws IOException {
        switch (detectFormat(path)) {
            case PLAIN_TEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGzip(path);
            case DIRECTORY:
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a plain text source.
     *
     * @param path source path
     * @return line stream; callers must close it
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry in a ZIP source.
     *
     * @param path ZIP source path
     * @return line stream; callers must close it
     * @throws IOException if the source cannot be opened or has no file entries
     */
    public static Stream<String> openZip(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        InputStream source = Files.newInputStream(path);
        ZipInputStream zipInput = null;
        try {
            zipInput = new ZipInputStream(source);
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                throw new IOException("ZIP source contains no file entries: " + path);
            }
            return lines(zipInput);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipInput == null ? source : zipInput, exception);
            throw exception;
        }
    }

    /**
     * Open a named entry in a ZIP source.
     *
     * @param path ZIP source path
     * @param entryName entry to open
     * @return line stream; callers must close it
     * @throws IOException if the source or entry cannot be opened
     */
    public static Stream<String> openZip(Path path, String entryName) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryName, "entryName");
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return lines(zipFile.getInputStream(entry), zipFile);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipFile, exception);
            throw exception;
        }
    }

    /**
     * Open all non-directory entries in a ZIP source in archive order.
     *
     * @param path ZIP source path
     * @return combined line stream; callers must close it
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openAllZipEntries(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        ZipFile zipFile = new ZipFile(path.toFile());
        List<InputStream> entryStreams = new ArrayList<>();
        try {
            for (ZipEntry entry : Collections.list(zipFile.entries())) {
                if (!entry.isDirectory()) {
                    entryStreams.add(zipFile.getInputStream(entry));
                }
            }
            if (entryStreams.isEmpty()) {
                zipFile.close();
                return Stream.empty();
            }
            SequenceInputStream input = new SequenceInputStream(Collections.enumeration(entryStreams));
            return lines(input, zipFile);
        } catch (IOException | RuntimeException exception) {
            for (InputStream entryStream : entryStreams) {
                closeAfterFailure(entryStream, exception);
            }
            closeAfterFailure(zipFile, exception);
            throw exception;
        }
    }

    /**
     * Open a GZIP source.
     *
     * @param path GZIP source path
     * @return line stream; callers must close it
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openGzip(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        InputStream source = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(source));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(source, exception);
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input, Closeable... additionalResources) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader, additionalResources));
    }

    private static void close(Closeable primary, Closeable... additionalResources) {
        UncheckedIOException failure = null;
        try {
            primary.close();
        } catch (IOException exception) {
            failure = new UncheckedIOException(exception);
        }
        for (Closeable resource : additionalResources) {
            try {
                resource.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = new UncheckedIOException(exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeAfterFailure(Closeable resource, Exception failure) {
        try {
            resource.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
