// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 * A file-system source containing GC log text.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_FIRST_BYTE = 0x1f;
    private static final int GZIP_MAGIC_SECOND_BYTE = 0x8b;
    private static final int ZIP_MAGIC_FIRST_BYTE = 0x50;
    private static final int ZIP_MAGIC_SECOND_BYTE = 0x4b;

    private final Path path;

    private GCLogSource(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    /**
     * Creates a source for the supplied path.
     *
     * @param path source path
     * @return source backed by the path
     */
    public static GCLogSource from(Path path) {
        return new GCLogSource(path);
    }

    /**
     * Discovers the source format from the path and its magic bytes.
     *
     * @return source format
     * @throws IOException if the path cannot be inspected
     */
    public Format format() throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_FIRST_BYTE && secondByte == GZIP_MAGIC_SECOND_BYTE) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_FIRST_BYTE && secondByte == ZIP_MAGIC_SECOND_BYTE) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the source size on disk in bytes.
     *
     * @return source byte size
     * @throws IOException if the size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as a stream of lines. ZIP sources expose the first
     * non-directory entry.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        Format sourceFormat = format();
        if (sourceFormat == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        if (sourceFormat == Format.ZIP) {
            return zipLines(null);
        }
        if (sourceFormat == Format.GZIP) {
            return readerLines(new GZIPInputStream(Files.newInputStream(path)));
        }
        throw new IOException("Unable to read directory " + path);
    }

    /**
     * Opens a named entry from a ZIP source as a stream of lines.
     *
     * @param entryName ZIP entry name
     * @return entry lines
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        if (format() != Format.ZIP) {
            throw new IOException("Not a ZIP source: " + path);
        }
        return zipLines(entryName);
    }

    /**
     * Lists non-directory entries in a ZIP source.
     *
     * @return ZIP entry names in archive order
     * @throws IOException if the source cannot be inspected
     */
    public List<String> zipEntries() throws IOException {
        if (format() != Format.ZIP) {
            throw new IOException("Not a ZIP source: " + path);
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private Stream<String> zipLines(String requestedEntry) throws IOException {
        ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!entry.isDirectory() && (requestedEntry == null || requestedEntry.equals(entry.getName()))) {
                    return readerLines(zipInput);
                }
            }
        } catch (IOException exception) {
            zipInput.close();
            throw exception;
        }

        zipInput.close();
        if (requestedEntry != null) {
            throw new IOException("ZIP entry not found: " + requestedEntry);
        }
        return Stream.empty();
    }

    private Stream<String> readerLines(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
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
