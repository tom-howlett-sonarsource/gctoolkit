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

/** File-system and compressed-stream operations shared by GC log consumers. */
public final class LogSource {
    private static final int GZIP_MAGIC = 0x1f8b;
    private static final int ZIP_MAGIC = 0x504b;

    private LogSource() { }

    /** Supported kinds of log source. */
    public enum Format { PLAIN_TEXT, ZIP, GZIP, DIRECTORY }

    /** Detects a source by its contents rather than its file extension. */
    public static Format format(Path source) throws IOException {
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(source)) {
            int first = input.read();
            int second = input.read();
            int magic = (first << 8) | second;
            if (magic == GZIP_MAGIC) return Format.GZIP;
            if (magic == ZIP_MAGIC) return Format.ZIP;
            return Format.PLAIN_TEXT;
        }
    }

    /** Returns direct child files for a directory, or the source itself for a file. */
    public static List<Path> discover(Path source) throws IOException {
        if (!Files.isDirectory(source)) return List.of(source);
        try (Stream<Path> children = Files.list(source)) {
            return children.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    /** Returns the names of all file entries in a ZIP source. */
    public static List<String> discoverZipEntries(Path source) throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            return zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /** Returns the total physical byte size of the source. */
    public static long size(Path source) throws IOException {
        if (!Files.isDirectory(source)) return Files.size(source);
        try (Stream<Path> files = Files.walk(source)) {
            long total = 0L;
            for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                total = Math.addExact(total, Files.size(file));
            }
            return total;
        }
    }

    /** Opens a plain, GZIP, or the first non-directory ZIP entry as a line stream. */
    public static Stream<String> open(Path source) throws IOException {
        switch (format(source)) {
            case PLAIN_TEXT: return Files.lines(source);
            case ZIP: return openZip(source);
            case GZIP: return openGzip(source);
            default: throw new IOException("Unable to open directory as a log stream: " + source);
        }
    }

    /** Opens the first non-directory entry of a ZIP source. */
    public static Stream<String> openZip(Path source) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) { }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + source);
        }
        return lines(input);
    }

    /** Opens a named entry of a ZIP source. */
    public static Stream<String> openZip(Path source, String entryName) throws IOException {
        ZipFile zip = new ZipFile(source.toFile());
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zip.close();
            throw new IOException("ZIP entry does not exist: " + entryName);
        }
        try {
            return lines(zip.getInputStream(entry)).onClose(() -> close(zip));
        } catch (IOException error) {
            zip.close();
            throw error;
        }
    }

    /** Opens a GZIP source as a line stream. */
    public static Stream<String> openGzip(Path source) throws IOException {
        return lines(new GZIPInputStream(Files.newInputStream(source)));
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Stream.close cannot report checked IO failures.
        }
    }
}
