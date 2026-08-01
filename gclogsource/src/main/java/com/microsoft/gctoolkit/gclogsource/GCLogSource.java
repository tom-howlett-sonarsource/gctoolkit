// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

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

/**
 * A readable GC log source. A source can be a plain file, a GZIP file, a ZIP file,
 * a single entry in a ZIP file, or a directory whose children can be discovered.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;
    private final String zipEntryName;

    private GCLogSource(Path path, Format format, String zipEntryName) {
        this.path = Objects.requireNonNull(path, "path");
        this.format = Objects.requireNonNull(format, "format");
        this.zipEntryName = zipEntryName;
    }

    /**
     * Create a source for a path. File formats are identified from their magic bytes,
     * so compressed files do not need a conventional filename extension.
     *
     * @param path source path
     * @return the source
     */
    public static GCLogSource from(Path path) {
        return new GCLogSource(path, detect(path), null);
    }

    /**
     * Create a source for one entry in a ZIP file.
     *
     * @param path path to the ZIP file
     * @param entryName name of the entry
     * @return the ZIP entry source
     */
    public static GCLogSource zipEntry(Path path, String entryName) {
        return new GCLogSource(path, Format.ZIP, Objects.requireNonNull(entryName, "entryName"));
    }

    /**
     * Discover readable sources below a path. A directory yields its immediate children,
     * a ZIP file yields its non-directory entries, and another file yields itself.
     *
     * @param path path to discover
     * @return discovered sources in filesystem or archive order
     * @throws IOException if the directory or ZIP file cannot be read
     */
    public static List<GCLogSource> discover(Path path) throws IOException {
        GCLogSource source = from(path);
        if (source.isDirectory()) {
            try (Stream<Path> children = Files.list(path)) {
                return children.map(GCLogSource::from).collect(Collectors.toList());
            }
        }
        if (source.isZip()) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                return zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(entry -> zipEntry(path, entry.getName()))
                        .collect(Collectors.toList());
            }
        }
        return List.of(source);
    }

    /**
     * Return the filesystem path containing this source.
     *
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the ZIP entry name, or the path's filename for a non-entry source.
     *
     * @return source name
     */
    public String getName() {
        if (zipEntryName != null) {
            return zipEntryName;
        }
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    /**
     * Return the source size in bytes. For a ZIP entry this is the uncompressed entry size;
     * for other sources it is the size of the file on disk.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be determined
     */
    public long sizeInBytes() throws IOException {
        if (zipEntryName == null) {
            return Files.size(path);
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(zipEntryName);
            if (entry == null) {
                throw new IOException("ZIP entry not found: " + zipEntryName);
            }
            if (entry.getSize() >= 0) {
                return entry.getSize();
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                long size = 0;
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    size += bytesRead;
                }
                return size;
            }
        }
    }

    /**
     * Open this source as a lazily read stream of lines. Closing the returned stream closes
     * all associated file and archive resources.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return gzipLines();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    public boolean isZip() {
        return format == Format.ZIP;
    }

    public boolean isGZip() {
        return format == Format.GZIP;
    }

    public boolean isPlainText() {
        return format == Format.PLAINTEXT;
    }

    public boolean isDirectory() {
        return format == Format.DIRECTORY;
    }

    private Stream<String> zipLines() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipEntryName == null
                    ? zipFile.stream().filter(candidate -> !candidate.isDirectory()).findFirst().orElse(null)
                    : zipFile.getEntry(zipEntryName);
            if (entry == null || entry.isDirectory()) {
                zipFile.close();
                return Stream.empty();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(zipFile.getInputStream(entry)), Charset.defaultCharset()));
            return closingLines(reader, reader, zipFile);
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private Stream<String> gzipLines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))), Charset.defaultCharset()));
        return closingLines(reader, reader);
    }

    private static Stream<String> closingLines(BufferedReader reader, Closeable... resources) {
        return reader.lines().onClose(() -> close(resources));
    }

    private static void close(Closeable... resources) {
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

    private static Format detect(Path path) {
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
            // Keep the existing behavior: an unreadable path is treated as plain text,
            // and opening it later reports the useful IOException to the caller.
        }
        return Format.PLAINTEXT;
    }

    private enum Format {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY
    }
}
