// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Filesystem and compression-aware access to GC log sources.
 */
public final class LogSource {
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private LogSource() {
    }

    /** The supported source formats. */
    public enum Format { PLAIN, ZIP, GZIP, DIRECTORY }

    /**
     * Finds the regular-file sources directly below a directory, or returns the
     * supplied regular file as the only source.
     */
    public static List<Path> discover(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (!Files.isDirectory(source)) {
            return List.of(source);
        }
        try (Stream<Path> children = Files.list(source)) {
            return children.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }
    }

    /** Returns the on-disk size of a source in bytes. */
    public static long size(Path source) throws IOException {
        return Files.size(Objects.requireNonNull(source, "source"));
    }

    /** Detects a source format from its filesystem type and magic bytes. */
    public static Format format(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(source)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
            return Format.PLAIN;
        }
    }

    /** Opens the bytes of a plain source, a GZIP payload, or the first ZIP file entry. */
    public static InputStream open(Path source) throws IOException {
        Format format = format(source);
        if (format == Format.PLAIN) {
            return Files.newInputStream(source);
        }
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(source));
        }
        if (format == Format.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(source));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                // Find the first file entry.
            }
            if (entry == null) {
                zip.close();
                throw new IOException("ZIP source contains no file entries: " + source);
            }
            return zip;
        }
        throw new IOException("Cannot open a directory as a log stream: " + source);
    }

    /** Opens a lazily read stream of lines and closes its source when closed. */
    public static Stream<String> lines(Path source) throws IOException {
        if (format(source) == Format.PLAIN) {
            return Files.lines(source);
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(open(source))));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked IO failures.
        }
    }
}
