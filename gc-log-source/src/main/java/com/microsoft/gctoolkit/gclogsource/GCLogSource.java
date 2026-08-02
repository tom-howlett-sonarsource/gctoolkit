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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A file-system GC log source whose compression format is detected from its
 * content. ZIP sources expose the first non-directory entry, matching the
 * single-log behavior used by GCToolKit's API and parser.
 */
public final class GCLogSource {

    /** First byte in the GZIP signature. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    /** Second byte in the GZIP signature. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    /** First byte in the ZIP signature. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second byte in the ZIP signature. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    /** Path to the source. */
    private final Path path;
    /** Detected source format. */
    private final Format format;

    private GCLogSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discover a GC log source and its format.
     *
     * @param path path to the source
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return new GCLogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return new GCLogSource(path, Format.ZIP);
            }
            return new GCLogSource(path, Format.PLAIN_TEXT);
        }
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
     * Return the detected source format.
     *
     * @return source format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Return the number of bytes occupied by the source file.
     *
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the source, transparently decoding ZIP or GZIP compression.
     *
     * @return decoded source stream
     * @throws IOException if the source cannot be opened or contains no
     *         readable ZIP entry
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
                throw new IOException("Unable to read " + path.toString());
        }
    }

    /**
     * Open the decoded source as a stream of text lines. Closing the returned
     * stream also closes the source file.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = openReader();
        return reader.lines().onClose(() -> close(reader));
    }

    private BufferedReader openReader() throws IOException {
        return new BufferedReader(new InputStreamReader(
                new BufferedInputStream(openStream()), charset()));
    }

    private Charset charset() {
        if (format == Format.PLAIN_TEXT) {
            return StandardCharsets.UTF_8;
        }
        return Charset.defaultCharset();
    }

    private InputStream openZipStream() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                throw new IOException("ZIP source contains no file entries: "
                        + path.toString());
            }
            return input;
        } catch (IOException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static void close(final BufferedReader reader) {
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
        /** An uncompressed text file. */
        PLAIN_TEXT,
        /** A ZIP file. */
        ZIP,
        /** A GZIP file. */
        GZIP,
        /** A directory containing log files. */
        DIRECTORY
    }
}
