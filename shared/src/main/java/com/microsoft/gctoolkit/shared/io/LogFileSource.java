// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A file-system log source whose format is discovered from its content.
 */
public final class LogFileSource {

    /** Number of bits used to encode one magic byte. */
    private static final int BITS_PER_BYTE = 8;
    /** First byte in the GZIP signature. */
    private static final int GZIP_MAGIC_1 = 0x1F;
    /** Second byte in the GZIP signature. */
    private static final int GZIP_MAGIC_2 = 0x8B;
    /** First byte in the ZIP signature. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second byte in the ZIP signature. */
    private static final int ZIP_MAGIC_2 = 0x4B;

    /** Path to the source. */
    private final Path path;
    /** Format discovered for the source. */
    private final Format format;

    private LogFileSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discover a log source from its path and leading bytes.
     *
     * @param path path to the source
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogFileSource from(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogFileSource(path, Format.DIRECTORY);
        }

        int magic = readMagic(path);
        if (magic == magic(GZIP_MAGIC_1, GZIP_MAGIC_2)) {
            return new LogFileSource(path, Format.GZIP);
        }
        if (magic == magic(ZIP_MAGIC_1, ZIP_MAGIC_2)) {
            return new LogFileSource(path, Format.ZIP);
        }
        return new LogFileSource(path, Format.PLAIN_TEXT);
    }

    /**
     * Test whether a source begins with two expected bytes.
     *
     * @param path path to the source
     * @param first expected first byte
     * @param second expected second byte
     * @return {@code true} when both bytes match
     * @throws IOException if the source cannot be inspected
     */
    public static boolean hasMagic(
            final Path path, final int first, final int second)
            throws IOException {
        Objects.requireNonNull(path, "path");
        return readMagic(path) == magic(first, second);
    }

    /**
     * Return the source path.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Return the discovered source format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Return the number of bytes occupied by the source file.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long sizeInBytes() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the source as a stream of lines. For ZIP sources, the first
     * non-directory entry is used.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    @SuppressFBWarnings(
            value = "OS_OPEN_STREAM",
            justification = "The returned stream owns and closes the reader.")
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }

        InputStream input = openCompressedStream();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new BufferedInputStream(input),
                        StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openCompressedStream() throws IOException {
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            return openZipStream();
        }
        throw new IOException("Unable to read " + path);
    }

    private InputStream openZipStream() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());

        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + path);
        }
        return input;
    }

    private static int readMagic(final Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return magic(input.read(), input.read());
        }
    }

    private static int magic(final int first, final int second) {
        return (first << BITS_PER_BYTE) | second;
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report a checked exception.
        }
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        /** Uncompressed text. */
        PLAIN_TEXT,
        /** ZIP-compressed content. */
        ZIP,
        /** GZIP-compressed content. */
        GZIP,
        /** File-system directory. */
        DIRECTORY
    }
}
