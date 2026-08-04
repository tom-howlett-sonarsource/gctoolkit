// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

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
 * Discovers and opens a file-system GC log source.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_BYTE_ONE = 0x1f;
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8b;
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4b;

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover the source format from the path and its leading magic bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_ONE && second == GZIP_MAGIC_BYTE_TWO) {
                return new LogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_BYTE_ONE && second == ZIP_MAGIC_BYTE_TWO) {
                return new LogSource(path, Format.ZIP);
            }
            return new LogSource(path, Format.PLAIN_TEXT);
        }
    }

    /**
     * Test the leading bytes of a source.
     *
     * @param path source path
     * @param first expected first byte
     * @param second expected second byte
     * @return {@code true} when both bytes match
     * @throws IOException if the source cannot be read
     */
    public static boolean hasMagic(Path path, int first, int second) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            return input.read() == first && input.read() == second;
        }
    }

    /**
     * Return the source size as stored on the file system.
     *
     * @param path source path
     * @return size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(path);
    }

    public Path getPath() {
        return path;
    }

    public Format getFormat() {
        return format;
    }

    public long sizeInBytes() throws IOException {
        return sizeInBytes(path);
    }

    public boolean isPlainText() {
        return format == Format.PLAIN_TEXT;
    }

    public boolean isZip() {
        return format == Format.ZIP;
    }

    public boolean isGZip() {
        return format == Format.GZIP;
    }

    public boolean isDirectory() {
        return format == Format.DIRECTORY;
    }

    /**
     * Open the source content. ZIP sources expose their first non-directory entry.
     *
     * @return source content stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case ZIP:
                return openFirstZipEntry();
            case GZIP:
                return new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream source content one line at a time.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        return lines(openStream());
    }

    /**
     * Stream a named ZIP entry one line at a time.
     *
     * @param entryName ZIP entry name
     * @return entry lines
     * @throws IOException if the entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        if (!isZip()) {
            throw new IOException(path + " is not a ZIP source");
        }

        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to find ZIP entry " + entryName + " in " + path);
            }
            return lines(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException exception) {
            close(zipFile);
            throw exception;
        }
    }

    /**
     * List all non-directory entries in a ZIP source.
     *
     * @return ZIP entry names
     * @throws IOException if the ZIP source cannot be opened
     */
    public List<String> entryNames() throws IOException {
        if (!isZip()) {
            throw new IOException(path + " is not a ZIP source");
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                zipInput.close();
                return InputStream.nullInputStream();
            }
            return zipInput;
        } catch (IOException | RuntimeException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
