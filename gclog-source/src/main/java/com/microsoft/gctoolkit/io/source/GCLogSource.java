// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Discovers the representation of a GC log source and opens its contents.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discovers a source from its path and leading bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return new GCLogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return new GCLogSource(path, Format.ZIP);
            }
            return new GCLogSource(path, Format.PLAIN_TEXT);
        }
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /**
     * Returns the number of bytes occupied by the source file.
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Lists readable source names. ZIP directories are omitted.
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            return Collections.singletonList(path.getFileName().toString());
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Opens lines from a plain or GZIP source, or the first file in a ZIP source.
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return firstZipEntryLines();
            case GZIP:
                return readerLines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens a named file in a ZIP source.
     */
    public Stream<String> lines(String entryName) throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Source is not a ZIP file: " + path);
        }
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException("ZIP entry not found: " + entryName);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
        return reader.lines().onClose(() -> close(reader, zipFile));
    }

    /**
     * Opens all files in a ZIP source in archive order.
     */
    public Stream<String> allZipLines() throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Source is not a ZIP file: " + path);
        }
        ZipFile zipFile = new ZipFile(path.toFile());
        Vector<InputStream> streams = new Vector<>();
        try {
            for (ZipEntry entry : Collections.list(zipFile.entries())) {
                if (!entry.isDirectory()) {
                    streams.add(zipFile.getInputStream(entry));
                }
            }
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
        SequenceInputStream input = new SequenceInputStream(streams.elements());
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return reader.lines().onClose(() -> close(reader, zipFile));
    }

    private Stream<String> firstZipEntryLines() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            input.close();
            throw new IOException("ZIP file contains no readable entries: " + path);
        }
        return readerLines(input);
    }

    private static Stream<String> readerLines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines();
    }

    private static void close(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // Stream.close cannot report checked IO failures.
            }
        }
    }

    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
