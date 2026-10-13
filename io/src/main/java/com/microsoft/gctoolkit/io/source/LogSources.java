// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Common IO operations for GC log sources. */
public final class LogSources {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSources() {
    }

    /** Detects a source by its contents rather than its file extension. */
    public static LogSourceFormat format(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return LogSourceFormat.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return LogSourceFormat.ZIP;
            }
            return LogSourceFormat.PLAIN_TEXT;
        }
    }

    /** Returns the number of stored bytes for a file source. */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /** Discovers the immediate children of a source directory. */
    public static List<Path> discover(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    /** Discovers non-directory entries in a ZIP source. */
    public static List<String> zipEntries(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream().filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName).collect(Collectors.toList());
        }
    }

    /** Opens a plain, GZIP, or first-entry ZIP source as lines. */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, format(path));
    }

    /** Opens a source whose format has already been discovered. */
    public static Stream<String> lines(Path path, LogSourceFormat format) throws IOException {
        if (format == LogSourceFormat.PLAIN_TEXT) {
            return Files.lines(path);
        }
        InputStream input;
        if (format == LogSourceFormat.GZIP) {
            input = new GZIPInputStream(Files.newInputStream(path));
        } else if (format == LogSourceFormat.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            do {
                entry = zip.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                zip.close();
                throw new IOException("ZIP source contains no files: " + path);
            }
            input = zip;
        } else {
            throw new IOException("Unable to open directory as a log stream: " + path);
        }
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
    }

    /** Opens one named ZIP entry as lines. */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException("ZIP entry not found: " + entryName);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
        return reader.lines().onClose(() -> close(zipFile));
    }

    /** Opens all non-directory ZIP entries, in archive order, as one line stream. */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        Vector<InputStream> inputs = new Vector<>();
        try {
            for (ZipEntry entry : java.util.Collections.list(zipFile.entries())) {
                if (!entry.isDirectory()) {
                    inputs.add(zipFile.getInputStream(entry));
                }
            }
            SequenceInputStream sequence = new SequenceInputStream(inputs.elements());
            BufferedReader reader = new BufferedReader(new InputStreamReader(sequence));
            return reader.lines().onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private static void close(ZipFile zipFile) {
        try {
            zipFile.close();
        } catch (IOException ignored) {
            // Stream.close cannot report an IOException.
        }
    }
}
