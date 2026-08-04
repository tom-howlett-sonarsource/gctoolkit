// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 * A file-system GC log source with shared format discovery and stream handling.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover a GC log source from its path and leading bytes.
     *
     * @param path path to the source
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return new GCLogSource(path, discover(path));
    }

    /**
     * Detect the source format from the file contents rather than its extension.
     *
     * @param path path to inspect
     * @return detected source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1 && secondByte == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1 && secondByte == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
            return Format.PLAINTEXT;
        }
    }

    /**
     * Return the source size as stored on disk.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.size(path);
    }

    /**
     * Return this source's path.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Return this source's discovered format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Return this source's size as stored on disk.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long byteSize() throws IOException {
        return byteSize(path);
    }

    /**
     * Open the source for uncompressed byte reading. For ZIP sources, the first
     * non-directory entry is selected.
     *
     * @return an input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openFirstZipEntry();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open the source as a lazily read stream of lines.
     *
     * @return lines whose close handler releases the underlying source
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAINTEXT) {
            return Files.lines(path);
        }
        return lines(openReader());
    }

    /**
     * List the non-directory entries in a ZIP source.
     *
     * @return ZIP entry names in archive order
     * @throws IOException if this is not a ZIP source or the entries cannot be read
     */
    public List<String> zipEntries() throws IOException {
        requireZip();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open a named ZIP entry as a lazily read stream of lines.
     *
     * @param entryName ZIP entry to open
     * @return lines whose close handler releases the ZIP source
     * @throws IOException if the entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        requireZip();
        BufferedReader reader = openReader(openZipEntry(entryName));
        return lines(reader);
    }

    private BufferedReader openReader() throws IOException {
        return openReader(openStream());
    }

    private static BufferedReader openReader(InputStream input) {
        return new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), Charset.defaultCharset()));
    }

    private static Stream<String> lines(BufferedReader reader) {
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    return zipStream;
                }
            }
            throw new IOException("ZIP source contains no files: " + path);
        } catch (IOException exception) {
            zipStream.close();
            throw exception;
        }
    }

    private InputStream openZipEntry(String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    return zipStream;
                }
            }
            throw new IOException("ZIP entry not found: " + entryName);
        } catch (IOException exception) {
            zipStream.close();
            throw exception;
        }
    }

    private void requireZip() throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Not a ZIP source: " + path);
        }
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot surface checked exceptions.
        }
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY
    }
}
