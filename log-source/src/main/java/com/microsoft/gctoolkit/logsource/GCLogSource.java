// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
 * A file-system GC log source whose format is discovered from its contents.
 * ZIP sources expose the first non-directory entry.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;
    private static final int COPY_BUFFER_SIZE = 8192;

    private final Path path;
    private final Format format;
    private final String zipEntryName;

    private GCLogSource(Path path, Format format, String zipEntryName) {
        this.path = path;
        this.format = format;
        this.zipEntryName = zipEntryName;
    }

    /**
     * Discovers the source format from the file's magic bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the path cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return new GCLogSource(path, discoverFormat(path), null);
    }

    /**
     * Creates a source for a named entry in a ZIP file.
     *
     * @param path ZIP file path
     * @param entryName entry to expose
     * @return the ZIP entry source
     * @throws IOException if the path cannot be inspected or is not a ZIP file
     */
    public static GCLogSource fromZipEntry(Path path, String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        GCLogSource source = from(path);
        if (source.format != Format.ZIP) {
            throw source.unsupportedFormat();
        }
        return new GCLogSource(path, Format.ZIP, entryName);
    }

    public Path getPath() {
        return path;
    }

    public Format getFormat() {
        return format;
    }

    /**
     * Lists the non-directory entries exposed by a ZIP source.
     *
     * @return entry names in archive order
     * @throws IOException if the source is not a ZIP or cannot be read
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            throw unsupportedFormat();
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Returns the number of uncompressed bytes exposed by this source.
     *
     * @return logical source size in bytes
     * @throws IOException if the source cannot be read
     */
    public long size() throws IOException {
        if (format == Format.PLAIN) {
            return Files.size(path);
        }
        if (format == Format.DIRECTORY) {
            throw unsupportedFormat();
        }

        long size = 0L;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (InputStream input = open()) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                size = Math.addExact(size, bytesRead);
            }
        }
        return size;
    }

    /**
     * Opens the uncompressed byte stream represented by this source.
     *
     * @return an input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN:
                return new BufferedInputStream(Files.newInputStream(path));
            case ZIP:
                return openZip();
            case GZIP:
                return openGzip();
            default:
                throw unsupportedFormat();
        }
    }

    /**
     * Opens the source as UTF-8 lines. Closing the stream closes the source.
     *
     * @return a lazily read stream of lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(open(), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static Format discoverFormat(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
            return Format.PLAIN;
        }
    }

    private InputStream openGzip() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return new BufferedInputStream(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private InputStream openZip() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && !isSelectedZipEntry(entry));
            if (entry == null && zipEntryName != null) {
                throw new IOException("ZIP entry not found: " + zipEntryName);
            }
            return new BufferedInputStream(input);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private IOException unsupportedFormat() {
        return new IOException("Unable to read " + path);
    }

    private boolean isSelectedZipEntry(ZipEntry entry) {
        if (entry.isDirectory()) {
            return false;
        }
        return zipEntryName == null || zipEntryName.equals(entry.getName());
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void closeAfterFailure(InputStream input, Exception failure) {
        try {
            input.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        PLAIN,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
