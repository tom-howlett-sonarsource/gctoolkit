// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A readable GC log source backed by a plain file, a GZIP file, or an entry in a ZIP file.
 */
public final class LogSource {

    private static final int GZIP_MAGIC1 = 0x1f;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private final Path path;
    private final String entryName;
    private final Format format;

    private LogSource(Path path, String entryName, Format format) {
        this.path = Objects.requireNonNull(path);
        this.entryName = entryName;
        this.format = Objects.requireNonNull(format);
    }

    /**
     * Select the single source represented by a path. For ZIP files, the first non-directory entry is selected.
     *
     * @param path source path
     * @return selected log source
     * @throws IOException if the path cannot be inspected
     */
    public static LogSource of(Path path) throws IOException {
        Format format = format(path);
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to open directory as a log source: " + path);
        }
        if (format != Format.ZIP) {
            return new LogSource(path, null, format);
        }

        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            String entryName = zipFile.stream()
                    .filter(candidate -> !candidate.isDirectory())
                    .map(ZipEntry::getName)
                    .findFirst()
                    .orElse(null);
            return new LogSource(path, entryName, Format.ZIP);
        }
    }

    /**
     * Select a named entry from a ZIP source.
     *
     * @param path ZIP path
     * @param entryName entry name
     * @return selected ZIP entry source
     * @throws IOException if the path is not a ZIP or the entry does not exist
     */
    public static LogSource zipEntry(Path path, String entryName) throws IOException {
        if (format(path) != Format.ZIP) {
            throw new IOException("Not a ZIP log source: " + path);
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
        }
        return new LogSource(path, entryName, Format.ZIP);
    }

    /**
     * Return all sources represented by a path. Directories yield their direct file children and ZIP files yield
     * their non-directory entries.
     *
     * @param path source path or directory
     * @return discovered sources
     * @throws IOException if a source cannot be inspected
     */
    public static List<LogSource> discover(Path path) throws IOException {
        Format format = format(path);
        if (format == Format.DIRECTORY) {
            List<LogSource> sources = new ArrayList<>();
            for (Path child : discoverPaths(path)) {
                if (!Files.isDirectory(child)) {
                    sources.add(of(child));
                }
            }
            return sources;
        }
        if (format != Format.ZIP) {
            return List.of(new LogSource(path, null, format));
        }

        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> new LogSource(path, entry.getName(), Format.ZIP))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Discover direct children of a directory, or return the supplied path as the only result.
     *
     * @param path path to inspect
     * @return discovered paths
     * @throws IOException if directory contents cannot be listed
     */
    public static List<Path> discoverPaths(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return List.of(path);
        }
        try (Stream<Path> paths = Files.list(path)) {
            return paths.collect(Collectors.toList());
        }
    }

    /**
     * Detect the source format from its leading bytes.
     *
     * @param path path to inspect
     * @return detected format
     * @throws IOException if the path cannot be read
     */
    public static Format format(Path path) throws IOException {
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
     * Return the number of uncompressed bytes in this source.
     *
     * @return uncompressed byte count
     * @throws IOException if the source cannot be read
     */
    public long size() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.size(path);
        }
        if (format == Format.ZIP) {
            if (entryName == null) {
                return 0;
            }
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                ZipEntry entry = zipFile.getEntry(entryName);
                if (entry == null) {
                    throw new IOException("ZIP entry not found: " + entryName);
                }
                long size = entry.getSize();
                return size >= 0 ? size : countBytes(zipFile.getInputStream(entry));
            }
        }
        if (format == Format.GZIP) {
            try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
                return countBytes(input);
            }
        }
        throw new IOException("Unable to size directory as a log source: " + path);
    }

    /**
     * Open this source as a stream of lines. Closing the stream closes all underlying resources.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        if (format == Format.GZIP) {
            return lines(new GZIPInputStream(Files.newInputStream(path)), null);
        }
        if (format == Format.ZIP) {
            if (entryName == null) {
                return Stream.empty();
            }
            ZipFile zipFile = new ZipFile(path.toFile());
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                zipFile.close();
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return lines(zipFile.getInputStream(entry), zipFile);
        }
        throw new IOException("Unable to open directory as a log source: " + path);
    }

    public Path path() {
        return path;
    }

    public String entryName() {
        return entryName;
    }

    public Format format() {
        return format;
    }

    private Stream<String> lines(InputStream input, ZipFile zipFile) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader, zipFile));
    }

    private static void close(BufferedReader reader, ZipFile zipFile) {
        IOException failure = null;
        try {
            reader.close();
        } catch (IOException exception) {
            failure = exception;
        }
        if (zipFile != null) {
            try {
                zipFile.close();
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

    private static long countBytes(InputStream input) throws IOException {
        try (InputStream source = input) {
            byte[] buffer = new byte[8192];
            long count = 0;
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                count += bytesRead;
            }
            return count;
        }
    }

    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
