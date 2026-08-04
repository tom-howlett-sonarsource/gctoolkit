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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * A file-system source for GC log data.
 *
 * <p>The source format is discovered from the first two bytes rather than the
 * file name, matching the behavior of the existing GC log data sources.</p>
 */
public final class LogSource {

    /** First byte in a GZIP signature. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    /** Second byte in a GZIP signature. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    /** First byte in a ZIP signature. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second byte in a ZIP signature. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    /** Discovered format. */
    private final Format format;
    /** Source path. */
    private final Path path;

    private LogSource(final Path sourcePath, final Format sourceFormat) {
        this.path = sourcePath;
        this.format = sourceFormat;
    }

    /**
     * Discover the format of a log source.
     *
     * <p>An unreadable or missing non-directory source is treated as plain
     * text. An attempt to open it will still report the underlying IO
     * error.</p>
     *
     * @param sourcePath source path
     * @return the discovered source
     */
    public static LogSource discover(final Path sourcePath) {
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
        } catch (IOException ignored) {
            // Preserve the previous metadata behavior; opening reports the
            // error.
        }
        return new LogSource(sourcePath, Format.PLAIN_TEXT);
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
     * Return the on-disk size of the source in bytes.
     *
     * @return source size in bytes
     * @throws IOException if the source size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the bytes represented by this source. ZIP sources are positioned at
     * their first non-directory entry and GZIP sources are decompressed.
     *
     * @return source input stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            case ZIP:
                return openFirstZipEntry();
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case DIRECTORY:
                throw new IOException("Unable to read directory " + path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a named entry in a ZIP source.
     *
     * @param entryName ZIP entry name
     * @return entry input stream
     * @throws IOException if this is not a ZIP source or the entry cannot be
     * opened
     */
    public InputStream open(final String entryName) throws IOException {
        Objects.requireNonNull(entryName, "entryName");
        if (format != Format.ZIP) {
            throw new IOException(path + " is not a ZIP source");
        }

        ZipInputStream zipStream =
                new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    return zipStream;
                }
            }
        } catch (IOException exception) {
            zipStream.close();
            throw exception;
        }
        zipStream.close();
        throw new IOException(
                "ZIP entry " + entryName + " not found in " + path);
    }

    /**
     * Stream the lines represented by this source. For ZIP files, lines come
     * from the first non-directory entry.
     *
     * @return stream of source lines
     * @throws IOException if the source cannot be read
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path, StandardCharsets.UTF_8);
        }
        return lines(open(), Charset.defaultCharset());
    }

    /**
     * Stream lines from a named ZIP entry.
     *
     * @param entryName ZIP entry name
     * @return stream of entry lines
     * @throws IOException if the entry cannot be read
     */
    public Stream<String> lines(final String entryName) throws IOException {
        return lines(open(entryName), Charset.defaultCharset());
    }

    /**
     * List non-directory entries in a ZIP source.
     *
     * @return ZIP entry names
     * @throws IOException if this is not a ZIP source or it cannot be read
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            throw new IOException(path + " is not a ZIP source");
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream zipStream =
                new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    return zipStream;
                }
            }
            return zipStream;
        } catch (IOException exception) {
            zipStream.close();
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input,
                                        final Charset charset) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input), charset));
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
        /** Plain text. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** Directory containing log segments. */
        DIRECTORY
    }
}
