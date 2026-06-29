// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared operations for detecting and streaming GC log source files.
 */
public final class LogSource {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSource() {
    }

    public static LogSourceFormat detectFormat(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }
        try (var inputStream = Files.newInputStream(path)) {
            int first = inputStream.read();
            int second = inputStream.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return LogSourceFormat.GZIP;
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return LogSourceFormat.ZIP;
            }
            return LogSourceFormat.PLAINTEXT;
        }
    }

    public static Stream<String> stream(Path path) throws IOException {
        return stream(path, detectFormat(path));
    }

    public static Stream<String> stream(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
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

    @SuppressWarnings("java:S2095")
    public static Stream<String> streamZipEntry(Path zipPath, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(zipPath.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException("Unable to read " + entryName + " from " + zipPath);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader, zipFile));
    }

    public static Stream<String> nonBlankLines(Stream<String> lines) {
        return lines
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isEmpty());
    }

    public static Stream<String> withEndOfData(Stream<String> lines, String endOfData) {
        return Stream.concat(lines, Stream.of(endOfData));
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipStream.close();
            throw new IOException("Unable to read " + path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Closing a stream must not mask stream pipeline exceptions.
            }
        }
    }
}
