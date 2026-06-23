// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared line-stream helpers for GC log source formats.
 */
public final class LogSourceStreams {

    private LogSourceStreams() {
    }

    public static Stream<String> stream(LogSourceMetadata metadata) throws IOException {
        return stream(metadata.getPath(), metadata.getFormat());
    }

    public static Stream<String> stream(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamFirstZipEntry(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                return Stream.empty();
        }
    }

    @SuppressWarnings("java:S2095")
    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                zipFile.close();
                return Stream.empty();
            }
            InputStream inputStream = zipFile.getInputStream(entry);
            return new java.io.BufferedReader(new InputStreamReader(inputStream))
                    .lines()
                    .onClose(closeAll(inputStream, zipFile));
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    public static List<String> zipEntryNames(Path path) throws IOException {
        try (var zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.collect(tail(numberOfLines));
        }
    }

    public static <T> Collector<T, ?, List<T>> tail(int numberOfLines) {
        if (numberOfLines < 0) {
            throw new IllegalArgumentException("numberOfLines must not be negative");
        }
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if (buffer.size() == numberOfLines) {
                buffer.pollFirst();
            }
            if (numberOfLines > 0) {
                buffer.addLast(line);
            }
        }, (left, right) -> {
            right.forEach(line -> {
                if (left.size() == numberOfLines) {
                    left.pollFirst();
                }
                if (numberOfLines > 0) {
                    left.addLast(line);
                }
            });
            return left;
        }, ArrayList::new);
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                zipStream.close();
                return Stream.empty();
            }
            return new java.io.BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)))
                    .lines()
                    .onClose(close(zipStream));
        } catch (IOException | RuntimeException exception) {
            zipStream.close();
            throw exception;
        }
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        try {
            return new java.io.BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)))
                    .lines()
                    .onClose(close(gzipStream));
        } catch (RuntimeException exception) {
            gzipStream.close();
            throw exception;
        }
    }

    private static Runnable close(Closeable closeable) {
        return () -> {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Stream.close() cannot throw checked exceptions.
            }
        };
    }

    private static Runnable closeAll(Closeable first, Closeable second) {
        return () -> {
            close(first).run();
            close(second).run();
        };
    }
}
