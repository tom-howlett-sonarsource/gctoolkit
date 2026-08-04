// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
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
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a GC log source. Compressed sources are identified by
 * their magic bytes rather than their file-name extension.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover the format of a GC log source.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be read
     */
    public static GCLogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return new GCLogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return new GCLogSource(path, Format.ZIP);
            }
            return new GCLogSource(path, Format.PLAIN_TEXT);
        }
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
     * Return the size of the source container. For ZIP and GZIP sources this is
     * the compressed size on disk.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long sizeInBytes() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the bytes represented by this source. For a ZIP source, the first
     * non-directory entry is opened.
     *
     * @return an input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            case ZIP:
                return openZipEntry(null);
            case GZIP:
                return new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path)));
            case DIRECTORY:
            default:
                throw new IOException("Unable to open GC log source " + path);
        }
    }

    /**
     * Open a named ZIP entry.
     *
     * @param entryName entry to open
     * @return an input stream owned by the caller
     * @throws IOException if this is not a ZIP source or the entry cannot be found
     */
    public InputStream openStream(String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        if (format != Format.ZIP) {
            throw new IOException("GC log source is not a ZIP file: " + path);
        }
        return openZipEntry(entryName);
    }

    /**
     * Open this source as lines using the platform default charset.
     *
     * @return a closeable stream of lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        return lines(openStream());
    }

    /**
     * Open a named ZIP entry as lines using the platform default charset.
     *
     * @param entryName entry to open
     * @return a closeable stream of lines
     * @throws IOException if the entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        return lines(openStream(entryName));
    }

    /**
     * Discover the non-directory entries in a ZIP source.
     *
     * @return entry names in archive order
     * @throws IOException if this is not a ZIP source or it cannot be read
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("GC log source is not a ZIP file: " + path);
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private InputStream openZipEntry(String entryName) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && (entryName == null || entryName.equals(entry.getName()))) {
                    return zipStream;
                }
            }
            if (entryName == null) {
                return zipStream;
            }
            throw new IOException("ZIP entry not found: " + entryName);
        } catch (IOException | RuntimeException exception) {
            try {
                zipStream.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, Charset.defaultCharset()));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
