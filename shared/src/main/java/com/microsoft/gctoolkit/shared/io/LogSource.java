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

/** A discovered file-system source for a garbage-collection log. */
public final class LogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    public enum Format { ZIP, GZIP, PLAINTEXT, DIRECTORY }

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /** Discovers a source using its file type and leading magic bytes. */
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
            return new LogSource(path, Format.PLAINTEXT);
        }
    }

    public Path path() { return path; }

    public Format format() { return format; }

    /** Returns the source's physical size in bytes. */
    public long size() throws IOException { return Files.size(path); }

    /** Opens the log as lines, using the first non-directory entry for ZIP files. */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAINTEXT) {
            return Files.lines(path);
        }
        if (format == Format.GZIP) {
            return lines(new GZIPInputStream(Files.newInputStream(path)));
        }
        if (format == Format.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                // Find the first file entry.
            }
            if (entry == null) {
                zip.close();
                throw new IOException("ZIP source contains no file entries: " + path);
            }
            return lines(zip);
        }
        throw new IOException("Unable to open directory as a single log source: " + path);
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked exceptions.
        }
    }
}
