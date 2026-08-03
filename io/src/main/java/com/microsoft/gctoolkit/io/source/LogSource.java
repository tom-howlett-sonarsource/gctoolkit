// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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

/** Operations shared by production consumers of GC log files. */
public final class LogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSource() {
    }

    /** Supported kinds of log source. */
    public enum Format {
        PLAIN,
        ZIP,
        GZIP,
        DIRECTORY
    }

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
            return Format.PLAIN;
        }
    }

    /** Finds regular files represented by a file or directory source. */
    public static List<Path> discover(Path source) throws IOException {
        if (!Files.isDirectory(source)) {
            return List.of(source);
        }
        try (Stream<Path> paths = Files.list(source)) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    /** Returns the source's on-disk byte size. */
    public static long byteSize(Path source) throws IOException {
        if (!Files.isDirectory(source)) {
            return Files.size(source);
        }
        long size = 0L;
        for (Path path : discover(source)) {
            size += Files.size(path);
        }
        return size;
    }

    public static List<String> zipEntries(Path source) throws IOException {
        try (ZipFile zipFile = new ZipFile(source.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /** Opens the first non-directory log in a plain, ZIP, or GZIP source. */
    public static Stream<String> lines(Path source) throws IOException {
        Format sourceFormat = format(source);
        switch (sourceFormat) {
            case PLAIN:
                return Files.lines(source);
            case ZIP:
                return lines(openFirstZipEntry(source));
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(source)));
            default:
                throw new IOException("Unable to open directory as a single log source: " + source);
        }
    }

    /** Opens a named entry in a ZIP source. */
    public static Stream<String> lines(Path source, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(source.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            BufferedReader reader = reader(zipFile.getInputStream(entry));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private static InputStream openFirstZipEntry(Path source) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + source);
        }
        return input;
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = reader(input);
        return reader.lines().onClose(() -> close(reader));
    }

    private static BufferedReader reader(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
    }

    private static void close(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // Stream.close cannot report an IOException.
            }
        }
    }
}
