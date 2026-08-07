// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Common file-system operations for GC log sources.
 */
public final class LogSource {

    /** First byte in a GZIP signature. */
    private static final int GZIP_MAGIC1 = 0x1F;
    /** Second byte in a GZIP signature. */
    private static final int GZIP_MAGIC2 = 0x8B;
    /** First byte in a ZIP signature. */
    private static final int ZIP_MAGIC1 = 0x50;
    /** Second byte in a ZIP signature. */
    private static final int ZIP_MAGIC2 = 0x4B;

    private LogSource() {
    }

    /**
     * Detect the kind of source at {@code path}.
     *
     * @param path source to inspect
     * @return the detected source format
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
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Return the physical size of a source in bytes.
     *
     * @param path source to size
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public static long byteSize(final Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Discover the non-directory entries in a ZIP source.
     *
     * @param path ZIP source to inspect
     * @return entry names in archive order
     * @throws IOException if the source cannot be inspected
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
     * Open a plain, ZIP, or GZIP source as lines. For ZIP files, the first
     * non-directory entry is opened.
     *
     * @param path source to open
     * @return lazily read lines; callers should close the stream
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
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a plain text source as lines.
     *
     * @param path source to open
     * @return lazily read lines; callers should close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openPlain(final Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry in a ZIP source as lines.
     *
     * @param path source to open
     * @return lazily read lines; callers should close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openZip(final Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    /**
     * Open a GZIP source as lines.
     *
     * @param path source to open
     * @return lazily read lines; callers should close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openGZip(final Path path) throws IOException {
        return lines(new GZIPInputStream(Files.newInputStream(path)));
    }

    /**
     * Open a named entry in a ZIP source as lines.
     *
     * @param path source to open
     * @param entryName name of the entry to open
     * @return lazily read lines; callers should close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openZipEntry(final Path path,
                                               final String entryName)
            throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                zipFile.close();
                return Stream.empty();
            }
            BufferedReader reader = reader(zipFile.getInputStream(entry));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    /**
     * Open named ZIP entries as one ordered stream of lines.
     *
     * @param path source to open
     * @param entryNames ordered names of the entries to open
     * @return lazily read lines; callers should close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openZipEntries(final Path path,
                                                 final List<String> entryNames)
            throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        List<InputStream> inputs = new ArrayList<>();
        try {
            for (String entryName : entryNames) {
                ZipEntry entry = zipFile.getEntry(entryName);
                if (entry != null && !entry.isDirectory()) {
                    inputs.add(zipFile.getInputStream(entry));
                }
            }
            SequenceInputStream sequence =
                    new SequenceInputStream(Collections.enumeration(inputs));
            BufferedReader reader = reader(sequence);
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            for (InputStream input : inputs) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // Preserve the exception which prevented the source from
                    // opening.
                }
            }
            zipFile.close();
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = reader(input);
        return reader.lines().onClose(() -> close(reader));
    }

    private static BufferedReader reader(final InputStream input) {
        return new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
    }

    private static void close(final AutoCloseable... resources) {
        IOException failure = null;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Supported GC log source formats. */
    public enum Format {
        /** Plain text source. */
        PLAIN_TEXT,
        /** ZIP-compressed source. */
        ZIP,
        /** GZIP-compressed source. */
        GZIP,
        /** Directory containing log sources. */
        DIRECTORY
    }
}
