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
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * File-system operations for GC log sources.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private LogSource() {
    }

    /**
     * The supported kinds of GC log source.
     */
    public enum Type {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    /**
     * Discover a source type from the file system and its magic bytes.
     *
     * @param path source path
     * @return source type
     * @throws IOException if the source cannot be inspected
     */
    public static Type typeOf(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Type.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Type.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Type.ZIP;
            }
            return Type.PLAIN_TEXT;
        }
    }

    /**
     * Return the source size as stored on disk.
     *
     * @param path source path
     * @return size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.size(path);
    }

    /**
     * Discover the immediate file-system entries in a directory.
     *
     * @param directory directory to inspect
     * @return lazily discovered paths; callers must close the stream
     * @throws IOException if the directory cannot be read
     */
    public static Stream<Path> discover(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        return Files.list(directory);
    }

    /**
     * Open a plain, ZIP, or GZIP source as lines. For ZIP files, the first
     * non-directory entry is opened.
     *
     * @param path source path
     * @return lazily read lines; callers must close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path path) throws IOException {
        Type type = typeOf(path);
        switch (type) {
            case PLAIN_TEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGzip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a plain-text source as lines without performing format discovery.
     *
     * @param path plain-text source path
     * @return lazily read lines; callers must close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.lines(path);
    }

    /**
     * Return the names of all non-directory entries in a ZIP source.
     *
     * @param path ZIP source path
     * @return entry names in archive order
     * @throws IOException if the ZIP source cannot be read
     */
    public static List<String> zipEntries(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (ZipFile file = new ZipFile(path.toFile())) {
            List<String> names = new ArrayList<>();
            file.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .forEach(names::add);
            return names;
        }
    }

    /**
     * Open one entry from a ZIP source as lines.
     *
     * @param path ZIP source path
     * @param entryName entry to open
     * @return lazily read lines; callers must close the stream
     * @throws IOException if the ZIP source or entry cannot be opened
     */
    public static Stream<String> openZipEntry(Path path, String entryName) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryName, "entryName");
        ZipFile file = new ZipFile(path.toFile());
        try {
            ZipEntry entry = file.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to find ZIP entry " + entryName + " in " + path);
            }
            BufferedReader reader = lineReader(file.getInputStream(entry));
            return reader.lines().onClose(() -> close(reader, file));
        } catch (IOException | RuntimeException exception) {
            try {
                file.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            BufferedReader reader = lineReader(input);
            return reader.lines().onClose(() -> close(reader));
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static Stream<String> openGzip(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            BufferedReader reader = lineReader(new GZIPInputStream(input));
            return reader.lines().onClose(() -> close(reader));
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static BufferedReader lineReader(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
    }

    private static void close(AutoCloseable... closeables) {
        IOException failure = null;
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } catch (Exception exception) {
                if (failure == null) {
                    failure = new IOException(exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }
}
