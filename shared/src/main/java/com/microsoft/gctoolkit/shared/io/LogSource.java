// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 * File-system operations shared by production GC log consumers.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSource() {
    }

    /** Supported GC log source formats. */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /** Discover the source format using its type and magic bytes. */
    public static Format format(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
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

    /** Return whether the source starts with the two supplied bytes. */
    public static boolean startsWith(Path path, int first, int second) throws IOException {
        try (InputStream input = Files.newInputStream(Objects.requireNonNull(path, "path"))) {
            return input.read() == first && input.read() == second;
        }
    }

    /** Return the physical size of a source in bytes. */
    public static long size(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /** Open a plain, GZIP, or the first file entry of a ZIP source. */
    public static InputStream open(Path path) throws IOException {
        Format format = format(path);
        if (format == Format.PLAIN_TEXT) {
            return Files.newInputStream(path);
        }
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
                // Find the first file entry.
            }
            if (entry == null) {
                input.close();
                throw new IOException("ZIP source contains no file entries: " + path);
            }
            return input;
        }
        throw new IOException("Cannot open a directory as a log stream: " + path);
    }

    /** Open one named file entry in a ZIP source. */
    public static InputStream openZipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException("ZIP entry not found: " + entryName);
        }
        InputStream input = zipFile.getInputStream(entry);
        return new BufferedInputStream(input) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zipFile.close();
                }
            }
        };
    }

    /** Stream UTF-8 lines from a plain, GZIP, or first-entry ZIP source. */
    public static Stream<String> lines(Path path) throws IOException {
        InputStream input = new BufferedInputStream(open(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    /** Stream UTF-8 lines from one named ZIP entry. */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                openZipEntry(path, entryName), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    /** Discover immediate children of a directory. */
    public static List<Path> files(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    /** Discover file entry names in a ZIP source. */
    public static List<String> zipEntries(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Stream.close cannot report checked I/O failures.
        }
    }
}
