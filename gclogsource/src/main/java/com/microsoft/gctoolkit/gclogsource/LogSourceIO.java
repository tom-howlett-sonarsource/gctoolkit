// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
 * Shared IO operations for GC log source discovery and streaming.
 */
public final class LogSourceIO {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private LogSourceIO() {
    }

    /**
     * Detect the storage format from the path type and leading magic bytes.
     *
     * @param path source selected by the caller
     * @return detected source format
     * @throws IOException if the source cannot be read
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
     * List regular files immediately contained by a directory.
     *
     * @param directory directory selected by the caller
     * @return discovered regular files
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> list(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .collect(Collectors.toList());
        }
    }

    /**
     * List non-directory entries in a ZIP source.
     *
     * @param path ZIP source selected by the caller
     * @return entry names in archive order
     * @throws IOException if the archive cannot be read
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory() && !isArchiveMetadata(entry.getName()))
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Count the files represented by a source.
     *
     * @param path source selected by the caller
     * @param format detected source format
     * @return number of files represented by the source
     * @throws IOException if discovery fails
     */
    public static int countFiles(Path path, LogSourceFormat format) throws IOException {
        Objects.requireNonNull(format, "format");
        if (format == LogSourceFormat.ZIP) {
            return zipEntryNames(path).size();
        }
        if (format == LogSourceFormat.DIRECTORY) {
            return list(path).size();
        }
        return 1;
    }

    /**
     * Stream lines from a plaintext, ZIP, or GZIP source. Closing the returned
     * stream closes every underlying IO resource.
     *
     * @param path source selected by the caller
     * @param format detected source format
     * @return lazily read lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> stream(Path path, LogSourceFormat format) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        if (format == LogSourceFormat.PLAINTEXT) {
            return Files.lines(path);
        }
        if (format == LogSourceFormat.ZIP) {
            return streamFirstZipEntry(path);
        }
        if (format == LogSourceFormat.GZIP) {
            return streamGZip(path);
        }
        throw new IOException("Unable to stream directory " + path);
    }

    /**
     * Stream a named, non-directory ZIP entry. Closing the returned stream
     * closes both the entry reader and its archive.
     *
     * @param path ZIP source selected by the caller
     * @param entryName exact archive entry name
     * @return lazily read entry lines
     * @throws IOException if the entry cannot be opened
     */
    @SuppressWarnings("java:S2095") // Resource ownership is transferred to the returned stream.
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
                    zipFile.getInputStream(entry), StandardCharsets.UTF_8));
            return managedLines(reader, zipFile);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipFile, exception);
            throw exception;
        }
    }

    /**
     * Read at most the requested number of lines from the end of a plaintext file.
     *
     * @param path plaintext source selected by the caller
     * @param numberOfLines maximum number of lines to return
     * @return tail lines in encounter order
     * @throws IOException if the source cannot be read
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        Objects.requireNonNull(path, "path");
        requireNonNegative(numberOfLines);
        if (numberOfLines == 0) {
            return List.of();
        }

        try (SeekableByteChannel channel = Files.newByteChannel(path)) {
            long start = findTailStart(channel, numberOfLines);
            channel.position(start);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Channels.newInputStream(channel), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }
        }
    }

    /**
     * Build a collector that retains only the requested number of trailing items.
     *
     * @param numberOfItems maximum number of items to retain
     * @param <T> collected item type
     * @return bounded tail collector
     */
    public static <T> Collector<T, ?, List<T>> tailCollector(int numberOfItems) {
        requireNonNegative(numberOfItems);
        return Collector.of(
                ArrayDeque::new,
                (Deque<T> values, T value) -> addTailValue(values, value, numberOfItems),
                (left, right) -> combineTailValues(left, right, numberOfItems),
                ArrayList::new);
    }

    @SuppressWarnings("java:S2095") // Resource ownership is transferred to the returned stream.
    private static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null && entry.isDirectory()) {
                entry = zipStream.getNextEntry();
            }
            if (entry == null) {
                throw new IOException("No readable entries found in " + path);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(zipStream), StandardCharsets.UTF_8));
            return managedLines(reader);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipStream, exception);
            throw exception;
        }
    }

    @SuppressWarnings("java:S2095") // Resource ownership is transferred to the returned stream.
    private static Stream<String> streamGZip(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        GZIPInputStream gzipStream;
        try {
            gzipStream = new GZIPInputStream(input);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(gzipStream), StandardCharsets.UTF_8));
        return managedLines(reader);
    }

    private static long findTailStart(SeekableByteChannel channel, int numberOfLines) throws IOException {
        long size = channel.size();
        long position = size;
        int separators = 0;
        int byteToRight = -1;
        ByteBuffer buffer = ByteBuffer.allocate(1);

        while (position > 0) {
            position--;
            channel.position(position);
            buffer.clear();
            if (channel.read(buffer) != 1) {
                throw new IOException("Unable to read log tail");
            }
            int current = Byte.toUnsignedInt(buffer.array()[0]);
            boolean lineSeparator = current == '\n' || current == '\r' && byteToRight != '\n';
            boolean trailingSeparator = position == size - 1;
            if (lineSeparator && !trailingSeparator) {
                separators++;
                if (separators == numberOfLines) {
                    return position + 1;
                }
            }
            byteToRight = current;
        }
        return 0;
    }

    private static <T> void addTailValue(Deque<T> values, T value, int maximumSize) {
        if (maximumSize == 0) {
            return;
        }
        if (values.size() == maximumSize) {
            values.removeFirst();
        }
        values.addLast(value);
    }

    private static boolean isArchiveMetadata(String entryName) {
        return Stream.of(entryName.split("/"))
                .anyMatch(segment -> "__MACOSX".equals(segment)
                        || ".DS_Store".equals(segment)
                        || segment.startsWith("._"));
    }

    private static <T> Deque<T> combineTailValues(Deque<T> left, Deque<T> right, int maximumSize) {
        right.forEach(value -> addTailValue(left, value, maximumSize));
        return left;
    }

    private static void requireNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Tail size must not be negative");
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
