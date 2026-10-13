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

/**
 * File-system operations common to consumers of GC log sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;

    public GCLogSource(Path path) {
        this.path = Objects.requireNonNull(path);
    }

    public Path path() {
        return path;
    }

    /** Returns the on-disk size of this source in bytes. */
    public long size() throws IOException {
        return Files.size(path);
    }

    /** Discovers the container format from the source itself, rather than its suffix. */
    public Format format() throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (size() < 2) {
            return Format.PLAIN_TEXT;
        }
        try (InputStream input = Files.newInputStream(path)) {
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

    /** Opens the source as lines, selecting plain, ZIP, or GZIP IO by magic bytes. */
    public Stream<String> lines() throws IOException {
        Format format = format();
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        if (format == Format.GZIP) {
            return lines(new GZIPInputStream(Files.newInputStream(path)));
        }
        if (format == Format.ZIP) {
            ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
                // Find the first file, matching the existing single-log behavior.
            }
            if (entry == null) {
                input.close();
                throw new IOException("ZIP source contains no file entries: " + path);
            }
            return lines(input);
        }
        throw new IOException("Unable to read directory as a single log source: " + path);
    }

    private static Stream<String> lines(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
    }

    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
