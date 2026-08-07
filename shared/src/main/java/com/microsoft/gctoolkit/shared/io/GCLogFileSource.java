// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a plain, ZIP, or GZIP GC log source.
 */
public final class GCLogFileSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC1 = 0x1F;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC2 = 0x8B;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC2 = 0x4B;

    /** Path to the source. */
    private final Path path;

    /**
     * Create a source rooted at {@code sourcePath}.
     *
     * @param sourcePath path to a GC log source
     */
    public GCLogFileSource(final Path sourcePath) {
        this.path = Objects.requireNonNull(sourcePath);
    }

    /**
     * @return the source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the number of bytes occupied by the source file.
     *
     * @return source size in bytes
     * @throws IOException when the source cannot be inspected
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Discover the source format from its type and magic bytes.
     *
     * @return the discovered source format
     */
    public Format getFormat() {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try {
            if (size() < 2) {
                return Format.PLAIN_TEXT;
            }
            try (InputStream input = Files.newInputStream(path)) {
                int first = input.read();
                int second = input.read();
                if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                    return Format.GZIP;
                }
                if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                    return Format.ZIP;
                }
            }
        } catch (IOException ignored) {
            // Preserve the historical behavior of treating unreadable paths
            // as plain text.
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Open the source as a lazily-read stream of lines. For a ZIP source,
     * the first non-directory entry is opened.
     *
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> stream() throws IOException {
        switch (getFormat()) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return streamZipFile();
            case GZIP:
                return streamGZipFile();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Stream<String> streamZipFile() throws IOException {
        ZipInputStream zipStream =
                new ZipInputStream(Files.newInputStream(path));
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

    private Stream<String> streamGZipFile() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        /** Plain text source. */
        PLAIN_TEXT,
        /** ZIP-compressed source. */
        ZIP,
        /** GZIP-compressed source. */
        GZIP,
        /** Directory source. */
        DIRECTORY
    }
}
