// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
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
 * A file-system GC log source and the shared operations for reading it.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_ONE = 0x1f;
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8b;
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4b;

    private final Path path;
    private final Type type;

    private GCLogSource(Path path, Type type) {
        this.path = path;
        this.type = type;
    }

    /**
     * Discover the source type from the path and its magic bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Type.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_ONE && second == GZIP_MAGIC_BYTE_TWO) {
                return new GCLogSource(path, Type.GZIP);
            }
            if (first == ZIP_MAGIC_BYTE_ONE && second == ZIP_MAGIC_BYTE_TWO) {
                return new GCLogSource(path, Type.ZIP);
            }
            return new GCLogSource(path, Type.PLAIN_TEXT);
        }
    }

    /**
     * @return the source path
     */
    public Path path() {
        return path;
    }

    /**
     * @return the discovered source type
     */
    public Type type() {
        return type;
    }

    /**
     * Return the number of bytes occupied by the source on disk. For a directory,
     * regular files below it are included recursively.
     *
     * @return physical source size in bytes
     * @throws IOException if the size cannot be determined
     */
    public long byteSize() throws IOException {
        if (type != Type.DIRECTORY) {
            return Files.size(path);
        }
        try (Stream<Path> paths = Files.walk(path)) {
            try {
                return paths.filter(Files::isRegularFile)
                        .mapToLong(GCLogSource::fileSize)
                        .sum();
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    /**
     * Return the number of uncompressed content bytes represented by the source.
     * ZIP directory entries do not contribute to the result.
     *
     * @return uncompressed content size in bytes
     * @throws IOException if the content cannot be read
     */
    public long contentByteSize() throws IOException {
        switch (type) {
            case PLAIN_TEXT:
            case DIRECTORY:
                return byteSize();
            case ZIP:
                return zipContentByteSize();
            case GZIP:
                try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
                    return countBytes(input);
                }
            default:
                throw unableToRead();
        }
    }

    /**
     * Discover regular files directly below a directory.
     *
     * @return discovered paths in file-system encounter order
     * @throws IOException if the directory cannot be listed
     */
    public List<Path> files() throws IOException {
        if (type != Type.DIRECTORY) {
            throw new IOException(path + " is not a directory");
        }
        try (Stream<Path> paths = Files.list(path)) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    /**
     * Discover non-directory entries in a ZIP source.
     *
     * @return ZIP entry names in archive order
     * @throws IOException if the archive cannot be read
     */
    public List<String> entries() throws IOException {
        if (type != Type.ZIP) {
            throw new IOException(path + " is not a ZIP source");
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open the source as UTF-8 lines. For ZIP sources, the first non-directory
     * entry is opened.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> open() throws IOException {
        switch (type) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openFirstZipEntry();
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw unableToRead();
        }
    }

    /**
     * Open one named entry from a ZIP source as UTF-8 lines.
     *
     * @param entryName entry to open
     * @return entry lines
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> open(String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        if (type != Type.ZIP) {
            throw new IOException(path + " is not a ZIP source");
        }

        InputStream fileInput = Files.newInputStream(path);
        ZipInputStream zipInput = new ZipInputStream(fileInput);
        try {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    return lines(zipInput);
                }
            }
            throw new IOException("ZIP entry not found: " + entryName);
        } catch (IOException | RuntimeException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private Stream<String> openFirstZipEntry() throws IOException {
        InputStream fileInput = Files.newInputStream(path);
        ZipInputStream zipInput = new ZipInputStream(fileInput);
        try {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    return lines(zipInput);
                }
            }
            throw new IOException("ZIP source contains no files: " + path);
        } catch (IOException | RuntimeException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private long zipContentByteSize() throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            try {
                return zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .mapToLong(entry -> zipEntrySize(zipFile, entry))
                        .sum();
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    private static long zipEntrySize(ZipFile zipFile, ZipEntry entry) {
        long size = entry.getSize();
        if (size >= 0) {
            return size;
        }
        try (InputStream input = zipFile.getInputStream(entry)) {
            return countBytes(input);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static long countBytes(InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            size += count;
        }
        return size;
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), StandardCharsets.UTF_8));
        return reader.lines().onClose(close(reader));
    }

    private static Runnable close(BufferedReader reader) {
        return () -> {
            try {
                reader.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
    }

    private IOException unableToRead() {
        return new IOException("Unable to read " + path);
    }

    /**
     * Supported GC log source types.
     */
    public enum Type {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
