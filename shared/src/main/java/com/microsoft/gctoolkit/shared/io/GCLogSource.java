// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A discovered GC log source that can report its stored size and open its content. */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private final Path path;
    private final Format format;
    private final long byteSize;

    private GCLogSource(Path path, Format format, long byteSize) {
        this.path = path;
        this.format = format;
        this.byteSize = byteSize;
    }

    /**
     * Discover a source from its path and leading bytes.
     * @param path source path
     * @return discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY, 0L);
        }

        long byteSize = Files.size(path);
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return new GCLogSource(path, Format.GZIP, byteSize);
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return new GCLogSource(path, Format.ZIP, byteSize);
            }
            return new GCLogSource(path, Format.PLAIN_TEXT, byteSize);
        }
    }

    public Path getPath() {
        return path;
    }

    public Format getFormat() {
        return format;
    }

    /** @return stored bytes, or zero for a directory source */
    public long getByteSize() {
        return byteSize;
    }

    /**
     * Open content, decompressing GZIP or the first file entry in ZIP.
     * @return content stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openFirstZipEntry();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
        boolean success = false;
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                // Continue to the first file entry.
            }
            if (entry == null) {
                throw new IOException("ZIP source contains no file entries: " + path);
            }
            success = true;
            return zip;
        } finally {
            if (!success) {
                zip.close();
            }
        }
    }

    /**
     * Open lines using the existing source conventions. Closing the returned stream closes the underlying source.
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        Charset charset = format == Format.PLAIN_TEXT ? StandardCharsets.UTF_8 : Charset.defaultCharset();
        BufferedReader reader = new BufferedReader(new InputStreamReader(openStream(), charset));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Source storage format. */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
