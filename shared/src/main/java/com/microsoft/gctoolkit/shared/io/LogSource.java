// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
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
 * Discovers and opens a plain, ZIP, or GZIP log source.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;
    private final int firstByte;
    private final int secondByte;

    private LogSource(Path path) {
        this.path = path;
        int[] magic = readMagic(path);
        this.firstByte = magic[0];
        this.secondByte = magic[1];
        this.format = discoverFormat(path, firstByte, secondByte);
    }

    /**
     * Discover the source format from its path and leading bytes.
     *
     * @param path path to a log source
     * @return the discovered source
     */
    public static LogSource from(Path path) {
        Objects.requireNonNull(path, "path");
        return new LogSource(path);
    }

    private static int[] readMagic(Path path) {
        int[] magic = {-1, -1};
        if (Files.isDirectory(path)) {
            return magic;
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            magic[0] = input.read();
            magic[1] = input.read();
        } catch (IOException ignored) {
            // Preserve the existing behavior: unreadable sources are treated as plain text
            // and report the I/O failure when they are opened.
        }
        return magic;
    }

    private static Format discoverFormat(Path path, int first, int second) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
            return Format.GZIP;
        }
        if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
            return Format.ZIP;
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * @return the source path
     */
    public Path path() {
        return path;
    }

    /**
     * @return the discovered source format
     */
    public Format format() {
        return format;
    }

    /**
     * Test the two leading bytes discovered for this source.
     *
     * @param first expected first byte
     * @param second expected second byte
     * @return whether the source starts with the expected bytes
     */
    public boolean startsWith(int first, int second) {
        return firstByte == first && secondByte == second;
    }

    /**
     * @return the number of bytes occupied by the source on disk
     * @throws IOException if the source size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the source as a lazy stream of lines. For a ZIP source, the first
     * non-directory entry is opened.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return firstZipEntryLines();
            case GZIP:
                return gzipLines();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open one named ZIP entry as a lazy stream of lines.
     *
     * @param entryName ZIP entry to open
     * @return entry lines
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Not a ZIP source: " + path);
        }

        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to find ZIP entry " + entryName + " in " + path);
            }
            return lines(zipFile.getInputStream(entry), zipFile);
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
    }

    /**
     * Discover the non-directory entries in a ZIP source.
     *
     * @return entry names in archive order
     * @throws IOException if the ZIP source cannot be read
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            return List.of();
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private Stream<String> firstZipEntryLines() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry firstEntry = zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .findFirst()
                    .orElse(null);
            if (firstEntry == null) {
                zipFile.close();
                return Stream.empty();
            }
            return lines(zipFile.getInputStream(firstEntry), zipFile);
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = new GZIPInputStream(Files.newInputStream(path));
        return lines(input, null);
    }

    private static Stream<String> lines(InputStream input, Closeable owner) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader, owner));
    }

    private static void close(Closeable reader, Closeable owner) {
        IOException failure = null;
        try {
            reader.close();
        } catch (IOException exception) {
            failure = exception;
        }
        if (owner != null) {
            try {
                owner.close();
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
     * Supported log source formats.
     */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
