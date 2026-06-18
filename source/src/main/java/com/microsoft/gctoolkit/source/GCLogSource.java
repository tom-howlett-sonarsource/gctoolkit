// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

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
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Low-level utilities for locating and streaming GC log sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;
    private static final int MAGIC_BYTES = 2;

    private static final Pattern ROTATING_LOG_PATTERN = Pattern.compile(".*\\.(\\d+)(\\.current)?$");
    private static final Pattern ROTATING_LOG_INDEX_PATTERN = Pattern.compile(".+\\.\\d+$");

    private GCLogSource() {
    }

    public static FileFormat fileFormat(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return FileFormat.DIRECTORY;
        }
        byte[] magic = new byte[MAGIC_BYTES];
        try (var inputStream = Files.newInputStream(path)) {
            int bytesRead = inputStream.read(magic);
            if (bytesRead < MAGIC_BYTES) {
                return FileFormat.PLAINTEXT;
            }
        }
        if (Byte.toUnsignedInt(magic[0]) == GZIP_MAGIC1 && Byte.toUnsignedInt(magic[1]) == GZIP_MAGIC2) {
            return FileFormat.GZIP;
        }
        if (Byte.toUnsignedInt(magic[0]) == ZIP_MAGIC1 && Byte.toUnsignedInt(magic[1]) == ZIP_MAGIC2) {
            return FileFormat.ZIP;
        }
        return FileFormat.PLAINTEXT;
    }

    public static Stream<String> stream(Path path, FileFormat fileFormat) throws IOException {
        switch (fileFormat) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamFirstZipEntry(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                return Stream.empty();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)))) {
                return reader.lines().collect(Collectors.toList()).stream();
            }
        }
    }

    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(Predicate.not(ZipEntry::isDirectory))
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    public static List<Path> discoverSegments(Path path, FileFormat fileFormat, String rootPattern) throws IOException {
        if (fileFormat == FileFormat.DIRECTORY) {
            return list(path);
        }
        Path parent = Optional.ofNullable(path.getParent()).orElse(path.toAbsolutePath().getParent());
        if (parent == null) {
            return List.of(path);
        }
        return list(parent).stream()
                .filter(file -> file.getFileName().toString().startsWith(rootPattern))
                .collect(Collectors.toList());
    }

    public static List<Path> list(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    public static String rootPattern(List<String> segmentNames, Path path, FileFormat fileFormat) {
        String[] bits;
        if (fileFormat == FileFormat.DIRECTORY) {
            bits = segmentNames.stream()
                    .filter(name -> !ROTATING_LOG_INDEX_PATTERN.matcher(name).matches())
                    .findFirst()
                    .orElseGet(() -> segmentNames.stream().findFirst().orElse(""))
                    .split("\\.");
        } else if (fileFormat == FileFormat.ZIP) {
            bits = segmentNames.stream().findFirst().orElse("").split("\\.");
        } else {
            bits = path.getFileName().toString().split("\\.");
        }
        int baseLength = baseLength(bits);
        if (baseLength == 0) {
            return "";
        }
        StringBuilder base = new StringBuilder(bits[0]);
        for (int i = 1; i < baseLength; i++) {
            base.append(".").append(bits[i]);
        }
        return base.toString();
    }

    public static int segmentIndex(Path path) {
        Matcher matcher = ROTATING_LOG_PATTERN.matcher(path.getFileName().toString());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

    public static boolean isCurrentSegment(Path path) {
        Matcher matcher = ROTATING_LOG_PATTERN.matcher(path.getFileName().toString());
        return !matcher.matches() || ".current".equals(matcher.group(2));
    }

    public static <T> Collector<T, ?, List<T>> tail(int numberOfLines) {
        if (numberOfLines <= 0) {
            return Collectors.filtering(item -> false, Collectors.toList());
        }
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if (buffer.size() == numberOfLines) {
                buffer.pollFirst();
            }
            buffer.addLast(line);
        }, (first, second) -> {
            second.forEach(item -> {
                if (first.size() == numberOfLines) {
                    first.pollFirst();
                }
                first.addLast(item);
            });
            return first;
        }, ArrayList::new);
    }

    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.collect(tail(numberOfLines));
        }
    }

    private static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        try (ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null && entry.isDirectory()) {
                entry = zipStream.getNextEntry();
            }
            if (entry == null) {
                return Stream.empty();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)))) {
                return reader.lines().collect(Collectors.toList()).stream();
            }
        }
    }

    private static Stream<String> streamGZip(Path path) throws IOException {
        try (GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
             BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)))) {
            return reader.lines().collect(Collectors.toList()).stream();
        }
    }

    private static int baseLength(String[] bits) {
        if (bits.length == 0 || bits[0].isEmpty()) {
            return 0;
        }
        if ("current".equals(bits[bits.length - 1])) {
            return bits.length - 2;
        }
        if (bits[bits.length - 1].matches("\\d+$")) {
            return bits.length - 1;
        }
        return bits.length;
    }

}
