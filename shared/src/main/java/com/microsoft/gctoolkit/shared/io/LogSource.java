// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A readable log source backed by a plain file, a GZIP file, or an entry in a ZIP file.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private final Path path;
    private final Format format;
    private final String zipEntryName;

    private LogSource(Path path, Format format, String zipEntryName) {
        this.path = path;
        this.format = format;
        this.zipEntryName = zipEntryName;
    }

    /**
     * Discovers readable sources represented by a path. ZIP entries and directory children are
     * returned in their underlying encounter order.
     *
     * @param path a file or directory
     * @return discovered sources
     * @throws IOException if the path cannot be inspected
     */
    public static List<LogSource> discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                List<Path> paths = children.collect(Collectors.toList());
                List<LogSource> sources = new ArrayList<>();
                for (Path child : paths) {
                    sources.addAll(discover(child));
                }
                return sources;
            }
        }

        Format detectedFormat = format(path);
        if (detectedFormat != Format.ZIP) {
            return List.of(new LogSource(path, detectedFormat, null));
        }

        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .map(name -> new LogSource(path, Format.ZIP, name))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Returns the first readable source represented by a path.
     *
     * @param path a file or directory
     * @return the first source
     * @throws IOException if the source cannot be inspected or is empty
     */
    public static LogSource first(Path path) throws IOException {
        return discover(path).stream()
                .findFirst()
                .orElseThrow(() -> new IOException("No readable log source in " + path));
    }

    /**
     * Detects the source format from its magic bytes.
     *
     * @param path source path
     * @return detected format
     * @throws IOException if the source cannot be read
     */
    public static Format format(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    public Optional<String> zipEntryName() {
        return Optional.ofNullable(zipEntryName);
    }

    public String name() {
        return zipEntryName == null ? path.getFileName().toString() : zipEntryName;
    }

    /**
     * Returns the number of uncompressed bytes in this source.
     *
     * @return uncompressed byte count
     * @throws IOException if the source cannot be read
     */
    public long byteSize() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.size(path);
        }
        try (InputStream input = openStream()) {
            long size = 0;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
            }
            return size;
        }
    }

    /**
     * Opens an uncompressed stream for this source. The caller owns the returned stream.
     *
     * @return source stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            return openZipEntry();
        }
        if (format == Format.PLAIN_TEXT) {
            return Files.newInputStream(path);
        }
        throw new IOException("Unable to open directory as a log source: " + path);
    }

    /**
     * Opens this source as a lazy stream of lines. Closing the line stream closes its input.
     *
     * @return line stream
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(openStream())));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openZipEntry() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(zipEntryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + zipEntryName);
            }
            InputStream input = zipFile.getInputStream(entry);
            return new FilterInputStream(input) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        zipFile.close();
                    }
                }
            };
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report an IOException.
        }
    }

    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
