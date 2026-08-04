// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

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
 * A file-system source containing a plain, ZIP, or GZIP GC log.
 *
 * <p>Compressed formats are discovered from their magic bytes rather than
 * their file names. For a ZIP source, the first entry that is not a directory
 * is treated as the log.</p>
 */
public final class GCLogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    /**
     * The supported source formats.
     */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP compressed content. */
        ZIP,
        /** GZIP compressed content. */
        GZIP,
        /** A directory rather than a single log source. */
        DIRECTORY
    }

    /** Source path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;
    /** Source file-system size. */
    private final long byteSize;

    private GCLogSource(final Path sourcePath, final Format sourceFormat,
                        final long sourceByteSize) {
        this.path = sourcePath;
        this.format = sourceFormat;
        this.byteSize = sourceByteSize;
    }

    /**
     * Discovers the format and byte size of a GC log source.
     *
     * @param sourcePath path to discover
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource discover(final Path sourcePath)
            throws IOException {
        Objects.requireNonNull(sourcePath, "path");
        return new GCLogSource(sourcePath, detectFormat(sourcePath),
                byteSize(sourcePath));
    }

    /**
     * Detects a source format using its type and leading magic bytes.
     *
     * @param sourcePath path to inspect
     * @return the detected format
     * @throws IOException if the source cannot be inspected
     */
    public static Format detectFormat(final Path sourcePath)
            throws IOException {
        Objects.requireNonNull(sourcePath, "path");
        if (Files.isDirectory(sourcePath)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(sourcePath)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the file-system size of a source in bytes.
     *
     * @param sourcePath source path
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(final Path sourcePath) throws IOException {
        Objects.requireNonNull(sourcePath, "path");
        return Files.size(sourcePath);
    }

    /**
     * Discovers and opens the source as a stream of lines.
     *
     * @param sourcePath source path
     * @return a stream which closes its underlying source when closed
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path sourcePath)
            throws IOException {
        return discover(sourcePath).stream();
    }

    /**
     * Returns the source path.
     *
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the discovered source format.
     *
     * @return source format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Returns the discovered file-system size.
     *
     * @return source size in bytes
     */
    public long getByteSize() {
        return byteSize;
    }

    /**
     * Opens this source as a stream of lines.
     *
     * @return a stream which closes its underlying source when closed
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
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());

            if (entry == null) {
                input.close();
                return Stream.empty();
            }
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedInputStream bufferedInput = new BufferedInputStream(input);
        return new BufferedReader(new InputStreamReader(bufferedInput)).lines();
    }
}
