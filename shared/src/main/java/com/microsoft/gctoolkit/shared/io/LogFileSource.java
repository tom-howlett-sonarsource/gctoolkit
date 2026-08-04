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
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a plain, ZIP, or GZIP log source.
 */
public final class LogFileSource {

    private static final int GZIP_MAGIC_BYTE_ONE = 0x1f;
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8b;
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4b;

    private final Path path;

    /**
     * Create a source for the supplied path. File-system access is deferred until
     * the source is inspected or opened.
     *
     * @param path path to a log source
     */
    public LogFileSource(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    /**
     * @return the source path
     */
    public Path path() {
        return path;
    }

    /**
     * Return the physical size of the source. For compressed sources this is the
     * compressed file size.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long sizeInBytes() throws IOException {
        return Files.size(path);
    }

    /**
     * Discover the source format from the path and its magic bytes.
     *
     * @return detected source format
     * @throws IOException if the source cannot be inspected
     */
    public Format format() throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (sizeInBytes() < 2) {
            return Format.PLAIN_TEXT;
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_ONE && secondByte == GZIP_MAGIC_BYTE_TWO) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_ONE && secondByte == ZIP_MAGIC_BYTE_TWO) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Open the source as a stream of lines. ZIP sources use their first
     * non-directory entry.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format()) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return gzipLines();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * List the file entries in a ZIP source.
     *
     * @return non-directory ZIP entry names
     * @throws IOException if the ZIP source cannot be opened
     */
    public List<String> zipEntryNames() throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open one named entry in a ZIP source as a stream of lines.
     *
     * @param entryName ZIP entry to open
     * @return entry lines
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException("Unable to find ZIP entry " + entryName + " in " + path);
        }

        try {
            return readerLines(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream zipInput = new ZipInputStream(bufferedInput());
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());

            if (entry == null) {
                throw new IOException("ZIP source contains no log entries: " + path);
            }
            return readerLines(zipInput);
        } catch (IOException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = bufferedInput();
        try {
            return readerLines(new GZIPInputStream(input));
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private InputStream bufferedInput() throws IOException {
        return new BufferedInputStream(Files.newInputStream(path));
    }

    private static Stream<String> readerLines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Supported log source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
