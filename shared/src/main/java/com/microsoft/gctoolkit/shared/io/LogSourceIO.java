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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * File-system and archive operations shared by GC log data sources.
 */
public final class LogSourceIO {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSourceIO() {
    }

    /** The supported kinds of GC log source. */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    /**
     * Discover a source's format from the file-system type and leading bytes.
     *
     * @param path source path
     * @return source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format format(Path path) throws IOException {
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

    /**
     * Discover the non-directory members represented by a source. Archive member names are
     * returned for ZIP files, directory children for directories, and the file name otherwise.
     *
     * @param path source path
     * @return member names in source order
     * @throws IOException if the source cannot be inspected
     */
    public static List<String> discover(Path path) throws IOException {
        Format format = format(path);
        if (format == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                return zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(ZipEntry::getName)
                        .collect(Collectors.toList());
            }
        }
        if (format == Format.DIRECTORY) {
            try (Stream<Path> children = Files.list(path)) {
                return children
                        .filter(child -> !Files.isDirectory(child))
                        .map(child -> child.getFileName().toString())
                        .collect(Collectors.toList());
            }
        }
        return List.of(path.getFileName().toString());
    }

    /**
     * Return the number of uncompressed bytes represented by a source. For a directory or ZIP
     * archive this is the sum of all non-directory members.
     *
     * @param path source path
     * @return uncompressed byte count
     * @throws IOException if the source cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        Format format = format(path);
        if (format == Format.PLAIN_TEXT) {
            return Files.size(path);
        }
        if (format == Format.DIRECTORY) {
            try (Stream<Path> children = Files.list(path)) {
                long total = 0L;
                for (Path child : children.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    total += Files.size(child);
                }
                return total;
            }
        }
        if (format == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                long total = 0L;
                for (ZipEntry entry : java.util.Collections.list(zipFile.entries())) {
                    if (!entry.isDirectory()) {
                        total += entry.getSize() >= 0 ? entry.getSize() : count(zipFile.getInputStream(entry));
                    }
                }
                return total;
            }
        }
        try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            return count(input);
        }
    }

    /**
     * Open a line stream for a plain or GZIP source, or the first non-directory ZIP member.
     * Closing the returned stream closes its underlying file or archive stream.
     *
     * @param path source path
     * @return source lines
     * @throws IOException if the source cannot be opened or is a directory
     */
    public static Stream<String> open(Path path) throws IOException {
        Format format = format(path);
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        if (format == Format.GZIP) {
            return lines(new GZIPInputStream(Files.newInputStream(path)));
        }
        if (format == Format.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            do {
                entry = zip.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(zip);
        }
        throw new IOException("Unable to open directory as a single log source: " + path);
    }

    /**
     * Open a line stream for one ZIP member.
     *
     * @param path ZIP source path
     * @param memberName archive member name
     * @return member lines
     * @throws IOException if the archive or member cannot be opened
     */
    public static Stream<String> openZipMember(Path path, String memberName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(memberName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException("ZIP member not found: " + memberName);
        }
        try {
            BufferedReader reader = reader(zipFile.getInputStream(entry));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = reader(input);
        return reader.lines().onClose(() -> close(reader));
    }

    private static BufferedReader reader(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
    }

    private static long count(InputStream input) throws IOException {
        try (InputStream closeable = input) {
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = closeable.read(buffer)) != -1) {
                total += read;
            }
            return total;
        }
    }

    private static void close(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Stream.close cannot report checked close failures.
            }
        }
    }
}
