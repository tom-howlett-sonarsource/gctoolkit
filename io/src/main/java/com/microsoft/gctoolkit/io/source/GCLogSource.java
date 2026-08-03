// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * A file-system GC log source whose compression format is discovered from its
 * content rather than its file name.
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
     * Discover a GC log source at {@code path}.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return new GCLogSource(path, discover(path));
    }

    /**
     * Discover the source format from its magic bytes.
     *
     * @param path source path
     * @return source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
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

    public Path getPath() {
        return path;
    }

    public Format getFormat() {
        return format;
    }

    /**
     * Return the number of bytes occupied by the source on disk.
     *
     * @return physical source size
     * @throws IOException if the size cannot be read
     */
    public long byteSize() throws IOException {
        return byteSize(path);
    }

    /**
     * Return the number of bytes occupied by a source on disk without first
     * discovering its format.
     *
     * @param path source path
     * @return physical source size
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Return the decoded size of the content read by {@link #openStream()}.
     * For ZIP sources this is the first non-directory entry.
     *
     * @return decoded content size
     * @throws IOException if the source cannot be read
     */
    public long decodedByteSize() throws IOException {
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to size directory " + path);
        }
        if (format == Format.PLAIN) {
            return byteSize();
        }
        if (format == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                ZipEntry entry = zipFile.stream()
                        .filter(candidate -> !candidate.isDirectory())
                        .findFirst()
                        .orElse(null);
                if (entry == null) {
                    return 0L;
                }
                if (entry.getSize() >= 0L) {
                    return entry.getSize();
                }
            }
        }
        try (InputStream input = openStream()) {
            long size = 0L;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
            }
            return size;
        }
    }

    /**
     * Open the decoded bytes in this source. ZIP sources expose the first
     * non-directory entry.
     *
     * @return decoded input stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openZipStream();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open the decoded source as a stream of lines.
     *
     * @return lines in the source
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN) {
            return Files.lines(path);
        }
        BufferedReader reader = openReader();
        return reader.lines().onClose(() -> close(reader));
    }

    private BufferedReader openReader() throws IOException {
        return new BufferedReader(new InputStreamReader(
                new BufferedInputStream(openStream()), StandardCharsets.UTF_8));
    }

    private InputStream openZipStream() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return input;
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
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
