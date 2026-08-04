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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * A file-system GC log source whose format is discovered from its content.
 *
 * <p>ZIP sources stream the first non-directory entry by default, matching the
 * single-log behavior in the API and parser modules.</p>
 */
public final class LogSource {

    /** First GZIP signature byte. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second GZIP signature byte. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First ZIP signature byte. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second ZIP signature byte. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    /** Source path. */
    private final Path path;
    /** Discovered source format. */
    private final Format format;

    private LogSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discover the source format using its first two bytes.
     *
     * @param path path to the source
     * @return a source for the path
     * @throws IOException if the path cannot be inspected
     */
    public static LogSource discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return new LogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return new LogSource(path, Format.ZIP);
            }
            return new LogSource(path, Format.PLAIN_TEXT);
        }
    }

    /**
     * Return the source path.
     *
     * @return the source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the discovered source format.
     *
     * @return the source format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Return the number of bytes occupied by the source file. For compressed
     * sources this is the compressed file size.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Open an uncompressed source as a stream of lines without format
     * discovery.
     *
     * @param path path to the plain-text source
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> streamPlain(final Path path)
            throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the source as a stream of lines.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> stream() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return streamPlain(path);
            case ZIP:
                return streamZipEntry(null);
            case GZIP:
                return streamGzip();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a named entry from a ZIP source as a stream of lines.
     *
     * @param entryName ZIP entry name
     * @return entry lines
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> stream(final String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        if (format != Format.ZIP) {
            throw new IOException("Unable to read ZIP entry from " + path);
        }
        return streamZipEntry(entryName);
    }

    /**
     * Return the non-directory entries in a ZIP source.
     *
     * @return immutable ZIP entry names
     * @throws IOException if the source cannot be opened
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            return Collections.emptyList();
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return Collections.unmodifiableList(zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList()));
        }
    }

    private Stream<String> streamZipEntry(final String entryName)
            throws IOException {
        ZipInputStream zipStream = new ZipInputStream(
                Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && (entry.isDirectory() || entryName != null
                    && !entryName.equals(entry.getName())));

            if (entryName != null && entry == null) {
                throw new IOException("ZIP entry " + entryName
                        + " not found in " + path);
            }
            return bufferedLines(zipStream);
        } catch (IOException | RuntimeException exception) {
            zipStream.close();
            throw exception;
        }
    }

    private Stream<String> streamGzip() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return bufferedLines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> bufferedLines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
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
        /** A ZIP archive. */
        ZIP,
        /** A GZIP-compressed source. */
        GZIP,
        /** An uncompressed text source. */
        PLAIN_TEXT,
        /** A directory containing source segments. */
        DIRECTORY
    }
}
