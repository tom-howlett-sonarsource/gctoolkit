// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a GC log source without depending on the API or parser modules.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.format = discover(path);
    }

    /**
     * Create a source for the supplied path and discover its format from the filesystem and magic bytes.
     *
     * @param path source path
     * @return the discovered source
     */
    public static GCLogSource from(Path path) {
        return new GCLogSource(path);
    }

    /**
     * Discover a source format. Unreadable non-directory paths remain plain text so the eventual open operation
     * reports the underlying I/O error, matching the historical behavior of the callers.
     *
     * @param path source path
     * @return discovered format
     */
    public static Format discover(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
        } catch (IOException ignored) {
            // Opening the source will surface the useful I/O exception to the caller.
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Return the source path.
     *
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the discovered format.
     *
     * @return source format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Return the on-disk size of the source in bytes.
     *
     * @return source size
     * @throws IOException if the size cannot be read
     */
    public long byteSize() throws IOException {
        return byteSize(path);
    }

    /**
     * Return the on-disk size of a source in bytes.
     *
     * @param path source path
     * @return source size
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Open this source as a lazily read stream of lines. ZIP sources use their first non-directory entry.
     * Closing the returned stream closes the underlying file or compressed stream.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> stream() throws IOException {
        switch (format) {
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

    private Stream<String> zipLines() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(zipStream);
        } catch (IOException | RuntimeException exception) {
            zipStream.close();
            throw exception;
        }
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private Stream<String> lines(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
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
