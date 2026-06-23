// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Opens line streams for GC log sources.
 */
public final class LogSourceStreams {

    private LogSourceStreams() {}

    public static Stream<String> stream(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamFirstZipEntry(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    public static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        try (ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());

            if (entry == null) {
                throw new IOException("No file entries found in " + path);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)))) {
                List<String> lines = reader.lines().collect(Collectors.toList());
                return lines.stream();
            }
        }
    }

    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        try (ZipFile file = new ZipFile(path.toFile())) {
            ZipEntry entry = file.getEntry(entryName);
            if (entry == null) {
                throw new IOException("Zip entry " + entryName + " not found in " + path);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(entry)))) {
                List<String> lines = reader.lines().collect(Collectors.toList());
                return lines.stream();
            }
        }
    }

    public static Stream<String> streamGZipFile(Path path) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)))))) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            return lines.stream();
        }
    }

    public static Stream<String> normalized(Stream<String> stream) {
        return stream
                .filter(Objects::nonNull)
                .filter(line -> !line.isBlank())
                .map(String::trim)
                .filter(line -> !line.isEmpty());
    }

    public static <T> Collector<T, ?, List<T>> tail(int n) {
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if (buffer.size() == n)
                buffer.pollFirst();
            buffer.add(line);
        }, (buffer, list) -> {
            while (list.size() < n && !buffer.isEmpty()) {
                list.addFirst(buffer.pollLast());
            }
            return list;
        }, ArrayList::new);
    }

    private static void close(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Nothing useful can be done while closing a stream callback.
            }
        }
    }
}
