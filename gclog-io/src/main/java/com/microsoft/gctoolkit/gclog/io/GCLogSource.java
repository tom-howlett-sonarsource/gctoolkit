// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.io;

import java.io.BufferedReader;
import java.io.Closeable;
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
 * A filesystem source for GC log data.
 */
public final class GCLogSource {

    /** First GZIP signature byte. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second GZIP signature byte. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First ZIP signature byte. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second ZIP signature byte. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    /** Source path. */
    private final Path path;
    /** Discovered source type. */
    private final Type type;

    private GCLogSource(final Path sourcePath) {
        this.path = Objects.requireNonNull(sourcePath, "path");
        this.type = discoverType(sourcePath);
    }

    /**
     * Creates a source and discovers its type from the path and leading bytes.
     *
     * @param path source path
     * @return the discovered source
     */
    public static GCLogSource from(final Path path) {
        return new GCLogSource(path);
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
     * Returns the source size in bytes.
     *
     * @return byte size
     * @throws IOException when the source cannot be measured
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Lists direct children of a directory source.
     *
     * @return child paths; closing the stream closes the directory listing
     * @throws IOException when the source is not a readable directory
     */
    public Stream<Path> files() throws IOException {
        if (type != Type.DIRECTORY) {
            throw new IOException("Not a directory source: " + path);
        }
        return Files.list(path);
    }

    /**
     * Lists non-directory entries in a ZIP source.
     *
     * @return ZIP entry names
     * @throws IOException when the ZIP cannot be read
     */
    public Stream<String> entries() throws IOException {
        if (type != Type.ZIP) {
            throw new IOException("Not a ZIP source: " + path);
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            List<String> names = zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
            return names.stream();
        }
    }

    /**
     * Opens lines from a plain, GZIP, or ZIP source. For ZIP files, the first
     * non-directory entry is opened.
     *
     * @return source lines; callers must close the stream
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (type) {
            case PLAINTEXT:
                return Files.lines(path);
            case GZIP:
                return gzipLines();
            case ZIP:
                return zipLines(null);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens lines from a named ZIP entry.
     *
     * @param entryName ZIP entry name
     * @return entry lines; callers must close the stream
     * @throws IOException when the source or entry cannot be opened
     */
    public Stream<String> lines(final String entryName) throws IOException {
        if (type != Type.ZIP) {
            throw new IOException("Not a ZIP source: " + path);
        }
        return zipLines(Objects.requireNonNull(entryName, "entryName"));
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new GZIPInputStream(input)));
            return reader.lines().onClose(() -> close(reader));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private Stream<String> zipLines(final String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = entryName == null
                    ? zipFile.stream()
                            .filter(candidate -> !candidate.isDirectory())
                            .findFirst()
                            .orElse(null)
                    : zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException(
                        "Unable to find a readable ZIP entry in " + path);
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zipFile.getInputStream(entry)));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private static Type discoverType(final Path path) {
        if (Files.isDirectory(path)) {
            return Type.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Type.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Type.ZIP;
            }
        } catch (IOException ignored) {
            // Preserve the existing behavior of treating unreadable paths as
            // plain files.
        }
        return Type.PLAINTEXT;
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

    /**
     * Supported GC log source types.
     */
    public enum Type {
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** Uncompressed text file. */
        PLAINTEXT,
        /** Filesystem directory. */
        DIRECTORY
    }
}
