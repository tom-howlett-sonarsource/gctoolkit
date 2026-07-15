// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared IO operations for discovering and reading GC log sources.
 */
public final class LogSourceIO {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;
    private static final int MAX_TAIL_BYTES = 1024 * 1024;

    private LogSourceIO() {
    }

    /**
     * Detect the source format from the path type and leading magic bytes.
     *
     * @param path source path
     * @return detected source format
     * @throws IOException if the source cannot be inspected
     */
    public static LogSourceFormat detectFormat(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }

        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(path))) {
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

    /**
     * Count the files represented by a source.
     *
     * @param format source format
     * @param path source path
     * @return number of files represented by the source
     * @throws IOException if entries cannot be discovered
     */
    public static int numberOfFiles(LogSourceFormat format, Path path) throws IOException {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(path, "path");
        switch (format) {
            case ZIP:
                return zipEntryNames(path).size();
            case DIRECTORY:
                return list(path).size();
            case PLAINTEXT:
            case GZIP:
                return Files.isRegularFile(path) ? 1 : 0;
            default:
                return 0;
        }
    }

    /**
     * List direct entries in a directory.
     *
     * @param path directory path
     * @return direct directory entries
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> list(Path path) throws IOException {
        try (Stream<Path> paths = Files.list(path)) {
            return paths.collect(Collectors.toList());
        }
    }

    /**
     * List non-directory entries in a ZIP source.
     *
     * @param path ZIP source path
     * @return entry names in archive order
     * @throws IOException if the ZIP cannot be read
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Stream lines from a single-file source. Closing the returned stream closes its source.
     *
     * @param format source format
     * @param path source path
     * @return lines from the source
     * @throws IOException if the source cannot be opened or the format is not streamable
     */
    public static Stream<String> stream(LogSourceFormat format, Path path) throws IOException {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(path, "path");
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamFirstZipEntry(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to stream GC log source format " + format);
        }
    }

    /**
     * Stream lines from one named ZIP entry. Closing the returned stream closes the ZIP file.
     *
     * @param path ZIP source path
     * @param entryName entry to stream
     * @return entry lines, or an empty stream when the entry does not exist or is a directory
     * @throws IOException if the ZIP or entry cannot be read
     */
    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                zipFile.close();
                return Stream.empty();
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8));
            return closeWithStream(reader, zipFile);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipFile, exception);
            throw exception;
        }
    }

    /**
     * Read at most the requested number of final lines without loading an unbounded line.
     *
     * @param path plaintext source path
     * @param numberOfLines maximum lines to return
     * @return final lines in source order
     * @throws IOException if the source cannot be read
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        if (numberOfLines < 0) {
            throw new IllegalArgumentException("numberOfLines must not be negative");
        }
        if (numberOfLines == 0) {
            return List.of();
        }

        byte[] tailBytes;
        long start;
        try (RandomAccessFile source = new RandomAccessFile(path.toFile(), "r")) {
            long length = source.length();
            start = Math.max(0, length - MAX_TAIL_BYTES);
            tailBytes = new byte[(int) (length - start)];
            source.seek(start);
            source.readFully(tailBytes);
        }

        int contentStart = start == 0 ? 0 : firstCompleteLine(tailBytes);
        if (contentStart == tailBytes.length) {
            return List.of();
        }

        Deque<String> lines = new ArrayDeque<>();
        String content = new String(tailBytes, contentStart, tailBytes.length - contentStart, StandardCharsets.UTF_8);
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() == numberOfLines) {
                    lines.removeFirst();
                }
                lines.addLast(line);
            }
        }
        return new ArrayList<>(lines);
    }

    private static int firstCompleteLine(byte[] bytes) {
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == '\n') {
                return index + 1;
            }
            if (bytes[index] == '\r') {
                return index + 1 < bytes.length && bytes[index + 1] == '\n' ? index + 2 : index + 1;
            }
        }
        return bytes.length;
    }

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
                    new BufferedInputStream(zipStream), StandardCharsets.UTF_8));
            return closeWithStream(reader);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipStream, exception);
            throw exception;
        }
    }

    private static Stream<String> streamGZip(Path path) throws IOException {
        InputStream source = Files.newInputStream(path);
        try {
            GZIPInputStream gzipStream = new GZIPInputStream(source);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(gzipStream), StandardCharsets.UTF_8));
            return closeWithStream(reader);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(source, exception);
            throw exception;
        }
    }

    private static Stream<String> closeWithStream(BufferedReader reader, Closeable... additionalCloseables) {
        return reader.lines().onClose(() -> closeAll(reader, additionalCloseables));
    }

    private static void closeAll(Closeable first, Closeable... remaining) {
        IOException failure = close(first, null);
        for (Closeable closeable : remaining) {
            failure = close(closeable, failure);
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }

    private static IOException close(Closeable closeable, IOException failure) {
        try {
            closeable.close();
        } catch (IOException exception) {
            if (failure == null) {
                return exception;
            }
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private static void closeAfterFailure(Closeable closeable, Exception failure) {
        try {
            closeable.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
