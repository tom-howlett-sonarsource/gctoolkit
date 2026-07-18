// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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
 * A logical GC log source backed by a file or an entry in a ZIP file.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final String entryName;
    private final Format format;

    private GCLogSource(Path path, String entryName, Format format) {
        this.path = Objects.requireNonNull(path);
        this.entryName = entryName;
        this.format = Objects.requireNonNull(format);
    }

    /**
     * Discover the logical log sources represented by a path. Directories yield their immediate
     * regular files, ZIP files yield their non-directory entries, and other files yield one source.
     *
     * @param path source file or directory
     * @return discovered sources in file-system or archive order
     * @throws IOException when the path cannot be inspected
     */
    public static List<GCLogSource> discover(Path path) throws IOException {
        Format discoveredFormat = format(path);
        if (discoveredFormat == Format.DIRECTORY) {
            try (Stream<Path> children = Files.list(path)) {
                return children
                        .filter(Files::isRegularFile)
                        .map(GCLogSource::fromPath)
                        .collect(Collectors.toList());
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
        if (discoveredFormat == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                return zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(ZipEntry::getName)
                        .map(entryName -> new GCLogSource(path, entryName, Format.ZIP))
                        .collect(Collectors.toList());
            }
        }
        return List.of(new GCLogSource(path, null, discoveredFormat));
    }

    /**
     * Create a source for a file path.
     *
     * @param path source file
     * @return source for the path
     * @throws IOException when the path cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Format discoveredFormat = format(path);
        if (discoveredFormat == Format.DIRECTORY) {
            throw new IOException("Cannot create a log source from a directory: " + path);
        }
        if (discoveredFormat == Format.ZIP) {
            return discover(path).stream()
                    .findFirst()
                    .orElseThrow(() -> new IOException("ZIP file contains no log sources: " + path));
        }
        return new GCLogSource(path, null, discoveredFormat);
    }

    /**
     * Create a source for a named ZIP entry.
     *
     * @param path ZIP file
     * @param entryName entry within the ZIP file
     * @return source for the entry
     */
    public static GCLogSource fromZipEntry(Path path, String entryName) {
        return new GCLogSource(path, Objects.requireNonNull(entryName), Format.ZIP);
    }

    private static GCLogSource fromPath(Path path) {
        try {
            return from(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Detect the format represented by a path.
     *
     * @param path path to inspect
     * @return detected format
     * @throws IOException when the path cannot be inspected
     */
    public static Format format(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_1 && secondByte == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_1 && secondByte == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Return the backing path.
     *
     * @return backing path
     */
    public Path path() {
        return path;
    }

    /**
     * Return the logical source name.
     *
     * @return ZIP entry name or file name
     */
    public String name() {
        return entryName == null ? path.getFileName().toString() : entryName;
    }

    /**
     * Return the source format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Return the uncompressed number of bytes in the logical source.
     *
     * @return uncompressed byte count
     * @throws IOException when the source cannot be read
     */
    public long size() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.size(path);
        }
        if (format == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                ZipEntry entry = requireEntry(zipFile);
                if (entry.getSize() >= 0) {
                    return entry.getSize();
                }
            }
        }
        try (InputStream input = openStream()) {
            long size = 0;
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                size += bytesRead;
            }
            return size;
        }
    }

    /**
     * Open the logical source as an uncompressed byte stream.
     *
     * @return source stream; closing it closes all backing resources
     * @throws IOException when the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.newInputStream(path);
        }
        if (format == Format.GZIP) {
            InputStream input = Files.newInputStream(path);
            try {
                return new GZIPInputStream(input);
            } catch (IOException | RuntimeException exception) {
                input.close();
                throw exception;
            }
        }
        if (format == Format.ZIP) {
            ZipFile zipFile = new ZipFile(path.toFile());
            try {
                InputStream input = zipFile.getInputStream(requireEntry(zipFile));
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
        throw new IOException("Cannot open a directory as a log source: " + path);
    }

    /**
     * Open the logical source as a lazily read stream of lines.
     *
     * @return line stream; callers must close it
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(openStream())));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private ZipEntry requireEntry(ZipFile zipFile) throws IOException {
        if (entryName == null) {
            throw new IOException("ZIP source requires an entry name: " + path);
        }
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("ZIP entry not found: " + entryName);
        }
        return entry;
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
