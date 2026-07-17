// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

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
import java.util.Vector;
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

    /** First byte in a GZIP header. */
    private static final int GZIP_MAGIC_FIRST = 0x1F;
    /** Second byte in a GZIP header. */
    private static final int GZIP_MAGIC_SECOND = 0x8B;
    /** First byte in a ZIP header. */
    private static final int ZIP_MAGIC_FIRST = 0x50;
    /** Second byte in a ZIP header. */
    private static final int ZIP_MAGIC_SECOND = 0x4B;

    private GCLogSource() {
    }

    /**
     * Discover the source format from its file-system type and magic bytes.
     *
     * @param path source path
     * @return discovered format
     * @throws IOException if the source cannot be inspected
     */
    public static Format format(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
            return Format.PLAIN;
        }
    }

    /**
     * Return the number of bytes occupied by the source file.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public static long byteSize(final Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open a plain, ZIP, or GZIP source as a stream of lines. For ZIP sources,
     * the first non-directory entry is opened.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        switch (format(path)) {
            case PLAIN:
                return Files.lines(path);
            case ZIP:
                return openFirstZipEntry(path);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * List the non-directory entries in a ZIP source.
     *
     * @param path ZIP source path
     * @return ZIP entry names in archive order
     * @throws IOException if the ZIP source cannot be opened
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
     * Open one entry from a ZIP source as a stream of lines.
     *
     * @param path ZIP source path
     * @param entryName entry name
     * @return entry lines
     * @throws IOException if the ZIP source or entry cannot be opened
     */
    public static Stream<String> openZipEntry(
            final Path path, final String entryName) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && (entry.isDirectory()
                    || !entryName.equals(entry.getName())));
            if (entry == null) {
                throw new IOException("Unable to find ZIP entry "
                        + entryName + " in " + path);
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    /**
     * Open every non-directory entry in a ZIP source in archive order.
     *
     * @param path ZIP source path
     * @return lines from all entries
     * @throws IOException if the ZIP source or an entry cannot be opened
     */
    public static Stream<String> openZipEntries(final Path path)
            throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        List<InputStream> openedStreams = new ArrayList<>();
        try {
            for (String entryName : zipEntries(zipFile)) {
                InputStream input = zipFile.getInputStream(
                        zipFile.getEntry(entryName));
                openedStreams.add(input);
            }
            SequenceInputStream input = new SequenceInputStream(
                    new Vector<>(openedStreams).elements());
            BufferedReader reader = reader(input);
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            close(openedStreams, zipFile);
            throw exception;
        }
    }

    private static Stream<String> openFirstZipEntry(final Path path)
            throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                throw new IOException("ZIP source contains no files: " + path);
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static List<String> zipEntries(final ZipFile zipFile) {
        return Collections.list(zipFile.entries()).stream()
                .filter(entry -> !entry.isDirectory())
                .map(ZipEntry::getName)
                .collect(Collectors.toList());
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = reader(input);
        return reader.lines().onClose(() -> close(reader));
    }

    private static BufferedReader reader(final InputStream input) {
        return new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), StandardCharsets.UTF_8));
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

    private static void close(
            final List<InputStream> inputs, final ZipFile zipFile) {
        List<Closeable> resources = new ArrayList<>(inputs);
        resources.add(zipFile);
        close(resources.toArray(new Closeable[0]));
    }

    /** Source formats supported by the toolkit. */
    public enum Format {
        /** Uncompressed file. */
        PLAIN,
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed file. */
        GZIP,
        /** File-system directory. */
        DIRECTORY
    }
}
