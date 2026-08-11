// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
 * A file-system source for GC log text.
 *
 * <p>The source format is discovered from the file contents rather than its
 * name. ZIP sources expose the first non-directory entry, matching the
 * single-log behavior used by GCToolKit.</p>
 */
public final class LogSource {

    /** First GZIP signature byte. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    /** Second GZIP signature byte. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    /** First ZIP signature byte. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second ZIP signature byte. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    /** Path to the source. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private LogSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discovers a log source and its format.
     *
     * @param sourcePath source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogSource discover(final Path sourcePath) throws IOException {
        Objects.requireNonNull(sourcePath, "path");
        if (Files.isDirectory(sourcePath)) {
            return new LogSource(sourcePath, Format.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(sourcePath)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return new LogSource(sourcePath, Format.GZIP);
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return new LogSource(sourcePath, Format.ZIP);
            }
            return new LogSource(sourcePath, Format.PLAIN_TEXT);
        }
    }

    /**
     * Returns the source path.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the discovered source format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the number of bytes in the source file.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source, transparently decoding ZIP and GZIP compression.
     * The caller owns the returned stream.
     *
     * @return decoded source stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            case ZIP:
                return openZip();
            case GZIP:
                return new BufferedInputStream(
                        new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens the source as text lines. Plain files use UTF-8, as
     * {@link Files#lines(Path)} does; compressed files retain the platform
     * charset behavior of the original callers. Closing the returned stream
     * closes the underlying file or compressed archive stream.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        Charset charset = format == Format.PLAIN_TEXT
                ? StandardCharsets.UTF_8
                : Charset.defaultCharset();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(open(), charset));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private InputStream openZip() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return new BufferedInputStream(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    /** Supported GC log source formats. */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** File-system directory. */
        DIRECTORY
    }
}
