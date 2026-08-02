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
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A filesystem GC log source and the common IO operations for it.
 */
public final class GCLogSource {

    /** First byte of the GZIP magic signature. */
    private static final int GZIP_MAGIC1 = 0x1f;
    /** Second byte of the GZIP magic signature. */
    private static final int GZIP_MAGIC2 = 0x8b;
    /** First byte of the ZIP magic signature. */
    private static final int ZIP_MAGIC1 = 0x50;
    /** Second byte of the ZIP magic signature. */
    private static final int ZIP_MAGIC2 = 0x4b;

    /** Filesystem source path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private GCLogSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discovers the source format from the path and its magic bytes.
     *
     * @param sourcePath source path
     * @return the discovered source
     * @throws IOException if the path cannot be inspected
     */
    public static GCLogSource discover(final Path sourcePath)
            throws IOException {
        Objects.requireNonNull(sourcePath, "path");
        if (Files.isDirectory(sourcePath)) {
            return new GCLogSource(sourcePath, Format.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(sourcePath)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return new GCLogSource(sourcePath, Format.GZIP);
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return new GCLogSource(sourcePath, Format.ZIP);
            }
            return new GCLogSource(sourcePath, Format.PLAIN_TEXT);
        }
    }

    /**
     * Returns the filesystem path.
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the discovered format.
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the source's on-disk size in bytes.
     * @return source size
     * @throws IOException if the source cannot be inspected
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the text payload. ZIP sources use their first non-directory entry.
     * @return opened payload
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
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
     * Opens the text payload as a lazily read stream of lines.
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        BufferedInputStream input = new BufferedInputStream(openStream());
        return new BufferedReader(new InputStreamReader(input)).lines();
    }

    private InputStream openZipStream() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry = input.getNextEntry();
        while (entry != null && entry.isDirectory()) {
            entry = input.getNextEntry();
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + path);
        }
        return input;
    }

    public enum Format {
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** Uncompressed text file. */
        PLAIN_TEXT,
        /** Directory containing log files. */
        DIRECTORY
    }
}
