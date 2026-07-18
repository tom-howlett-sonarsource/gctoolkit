// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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
 * Shared IO operations for discovering and opening GC log sources.
 */
public final class LogSourceIO {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private LogSourceIO() {
    }

    public static LogSourceFormat detectFormat(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_BYTE_1 && second == GZIP_MAGIC_BYTE_2) {
                return LogSourceFormat.GZIP;
            }
            if (first == ZIP_MAGIC_BYTE_1 && second == ZIP_MAGIC_BYTE_2) {
                return LogSourceFormat.ZIP;
            }
            return LogSourceFormat.PLAINTEXT;
        }
    }

    public static long byteSize(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isDirectory(path)) {
            return Files.size(path);
        }

        long total = 0L;
        for (Path source : list(path)) {
            if (Files.isRegularFile(source)) {
                total += Files.size(source);
            }
        }
        return total;
    }

    public static List<Path> list(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    public static List<String> zipEntryNames(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    public static Stream<String> stream(Path path, LogSourceFormat format) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamFirstZipEntry(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to stream directory " + path);
        }
    }

    public static Stream<String> stream(LogSourceFormat format, Path path) throws IOException {
        return stream(path, format);
    }

    @SuppressWarnings("java:S2095")
    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryName, "entryName");
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to read " + entryName + " from " + path);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    zipFile.getInputStream(entry)));
            return managedLines(reader, zipFile);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipFile, exception);
            throw exception;
        }
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null && entry.isDirectory()) {
                entry = zipStream.getNextEntry();
            }
            if (entry == null) {
                zipStream.close();
                return Stream.empty();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(zipStream)));
            return managedLines(reader);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipStream, exception);
            throw exception;
        }
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamGZip(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            GZIPInputStream gzipStream = new GZIPInputStream(input);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(gzipStream)));
            return managedLines(reader);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private static Stream<String> managedLines(BufferedReader reader, AutoCloseable... additionalResources) {
        return reader.lines().onClose(() -> closeAll(reader, additionalResources));
    }

    private static void closeAll(BufferedReader reader, AutoCloseable... additionalResources) {
        IOException failure = close(reader, null);
        for (AutoCloseable resource : additionalResources) {
            failure = close(resource, failure);
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }

    private static IOException close(AutoCloseable resource, IOException failure) {
        try {
            resource.close();
        } catch (Exception exception) {
            IOException closeFailure = exception instanceof IOException
                    ? (IOException) exception
                    : new IOException("Unable to close log source", exception);
            if (failure == null) {
                return closeFailure;
            }
            failure.addSuppressed(closeFailure);
        }
        return failure;
    }

    private static void closeAfterFailure(AutoCloseable resource, Exception failure) {
        try {
            resource.close();
        } catch (Exception closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
