// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A file-system GC log source that discovers its format from its contents and
 * opens plain-text, ZIP, and GZIP sources consistently.
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
     * Discovers and creates a source for {@code path}.
     *
     * @param path path to a GC log source
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Path sourcePath = Objects.requireNonNull(path, "path");
        return new GCLogSource(sourcePath, discover(sourcePath));
    }

    /**
     * Discovers the source format using the file's magic bytes rather than its
     * name or extension.
     *
     * @param path path to inspect
     * @return the discovered format
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
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the source path.
     *
     * @return the source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the discovered source format.
     *
     * @return the source format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Returns the number of bytes occupied by the source file. For compressed
     * sources this is the compressed file size.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long sizeInBytes() throws IOException {
        return sizeInBytes(path);
    }

    /**
     * Returns the number of bytes occupied by a source path.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Opens the source's log content as bytes. ZIP sources are positioned at
     * the first non-directory entry, matching single-log behavior.
     *
     * @return an input stream for the log content
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case ZIP:
                return openZipStream();
            case GZIP:
                return new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens the source as a lazy stream of lines.
     *
     * @return source content as lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(openStream(), Charset.defaultCharset()));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openZipStream() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return new BufferedInputStream(zipStream);
        } catch (IOException exception) {
            try {
                zipStream.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
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
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
