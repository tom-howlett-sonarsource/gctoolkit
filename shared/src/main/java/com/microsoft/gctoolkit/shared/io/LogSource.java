// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

/**
 * A file-system log source whose container format is discovered from its content.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discovers a source's format using its path and leading bytes.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogSource discover(Path path) throws IOException {
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

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /**
     * Returns the number of bytes occupied by this source on the file system.
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Opens the source as lines. For ZIP sources, the first non-directory entry is used.
     * Closing the returned stream closes all underlying IO resources.
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to read directory " + path);
        }

        if (format == Format.ZIP) {
            List<String> entries = entries();
            if (entries.isEmpty()) {
                throw new IOException("ZIP source contains no log entries: " + path);
            }
            return lines(entries.get(0));
        }

        InputStream input = Files.newInputStream(path);
        try {
            if (format == Format.GZIP) {
                input = new GZIPInputStream(input);
            }
            Charset charset = format == Format.PLAIN_TEXT
                    ? StandardCharsets.UTF_8
                    : Charset.defaultCharset();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new BufferedInputStream(input), charset));
            return reader.lines().onClose(() -> close(reader));
        } catch (IOException | RuntimeException exception) {
            close(input);
            throw exception;
        }
    }

    /**
     * Lists the non-directory entries in a ZIP source in archive order.
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Source is not a ZIP file: " + path);
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Opens one named entry from a ZIP source as lines.
     */
    public Stream<String> lines(String entryName) throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Source is not a ZIP file: " + path);
        }

        ZipFile zip = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(zip.getInputStream(entry))));
            return reader.lines().onClose(() -> {
                close(reader);
                close(zip);
            });
        } catch (IOException | RuntimeException exception) {
            close(zip);
            throw exception;
        }
    }

    private static void close(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Stream.close() cannot report checked exceptions.
        }
    }

    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
