// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static java.util.stream.Collectors.toList;

public final class GCLogSource {

    /** First byte of the GZIP signature. */
    private static final int GZIP_MAGIC_BYTE_ONE = 0x1f;
    /** Second byte of the GZIP signature. */
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8b;
    /** First byte of the ZIP signature. */
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    /** Second byte of the ZIP signature. */
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4b;

    private GCLogSource() {
    }

    /**
     * Discovers the source format from its path and signature bytes.
     *
     * @param path source path
     * @return discovered format
     * @throws IOException when the source cannot be read
     */
    public static Format format(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_ONE
                    && secondByte == GZIP_MAGIC_BYTE_TWO) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_ONE
                    && secondByte == ZIP_MAGIC_BYTE_TWO) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the physical size of a source in bytes.
     *
     * @param path source path
     * @return physical byte size
     * @throws IOException when the source metadata cannot be read
     */
    public static long byteSize(final Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Discovers immediate children of a source directory.
     *
     * @param directory directory to inspect
     * @return children ordered by file name
     * @throws IOException when the directory cannot be listed
     */
    public static List<Path> discover(final Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString()))
                    .collect(toList());
        }
    }

    /**
     * Discovers non-directory entries in a ZIP source.
     *
     * @param path ZIP source path
     * @return entry names in archive order
     * @throws IOException when the ZIP source cannot be read
     */
    public static List<String> zipEntries(final Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }

    /**
     * Opens lines from a plain, GZIP, or first-entry ZIP source.
     *
     * @param path source path
     * @return closeable stream of lines
     * @throws IOException when the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        switch (format(path)) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            case ZIP:
                return openFirstZipEntry(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens lines from one named ZIP entry.
     *
     * @param path ZIP source path
     * @param entryName entry to open
     * @return closeable stream of lines
     * @throws IOException when the source or entry cannot be opened
     */
    public static Stream<String> openZipEntry(
            final Path path, final String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException(
                    "Unable to find ZIP entry " + entryName + " in " + path);
        }

        try {
            BufferedReader reader = reader(zipFile.getInputStream(entry));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            close(zipFile);
            throw exception;
        }
    }

    /**
     * Opens lines from named ZIP entries in the supplied order.
     *
     * @param path ZIP source path
     * @param entryNames entries to open
     * @return closeable stream of lines
     * @throws IOException when the source or an entry cannot be opened
     */
    public static Stream<String> openZipEntries(
            final Path path, final List<String> entryNames) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        List<InputStream> inputs = new ArrayList<>();
        try {
            for (String entryName : entryNames) {
                ZipEntry entry = zipFile.getEntry(entryName);
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("Unable to find ZIP entry "
                            + entryName + " in " + path);
                }
                inputs.add(zipFile.getInputStream(entry));
            }
            Vector<InputStream> orderedInputs = new Vector<>(inputs);
            SequenceInputStream sequence =
                    new SequenceInputStream(orderedInputs.elements());
            BufferedReader reader = reader(sequence);
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            close(inputs, zipFile);
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
                throw new IOException(
                        "Unable to find a readable ZIP entry in " + path);
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = reader(input);
        return reader.lines().onClose(() -> close(reader));
    }

    private static BufferedReader reader(final InputStream input) {
        return new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), Charset.defaultCharset()));
    }

    private static void close(
            final List<InputStream> inputs, final ZipFile zipFile) {
        for (int index = inputs.size() - 1; index >= 0; index--) {
            close(inputs.get(index));
        }
        close(zipFile);
    }

    private static void close(final AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception ignored) {
            }
        }
    }

    public enum Format {
        /** Plain text source. */
        PLAIN_TEXT,
        /** ZIP archive source. */
        ZIP,
        /** GZIP compressed source. */
        GZIP,
        /** Directory source. */
        DIRECTORY
    }
}
