// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gcsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
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
 * Utility methods for discovering and reading GC log source files.
 */
public final class GCLogSources {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSources() {
    }

    public static GCLogFileFormat detectFormat(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return GCLogFileFormat.DIRECTORY;
        }

        try (var input = Files.newInputStream(path)) {
            int magicByte1 = input.read();
            int magicByte2 = input.read();
            if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2) {
                return GCLogFileFormat.GZIP;
            }
            if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2) {
                return GCLogFileFormat.ZIP;
            }
        }
        return GCLogFileFormat.PLAINTEXT;
    }

    public static List<Path> list(Path path) throws IOException {
        try (Stream<Path> paths = Files.list(path)) {
            return paths.collect(Collectors.toList());
        }
    }

    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    public static int countFiles(Path path, GCLogFileFormat format) throws IOException {
        if (format == GCLogFileFormat.ZIP) {
            return zipEntryNames(path).size();
        }
        if (format == GCLogFileFormat.DIRECTORY) {
            return list(path).size();
        }
        return 1;
    }

    public static Stream<String> stream(Path path, GCLogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamFirstZipEntry(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unable to stream " + path);
        }
    }

    @SuppressWarnings("java:S2095")
    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        return new ZipEntryLineSource(path, entryName).lines();
    }

    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            char eol = findEndOfLine(randomAccessFile);
            int linesFound = countLinesFromEnd(randomAccessFile, eol, numberOfLines);
            List<String> lines = new ArrayList<>();
            if (linesFound > 0) {
                String line;
                while ((line = randomAccessFile.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    public static <T> Collector<T, ?, List<T>> tailCollector(int numberOfLines) {
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if (buffer.size() == numberOfLines) {
                buffer.pollFirst();
            }
            buffer.add(line);
        }, (buffer, result) -> {
            while (result.size() < numberOfLines && !buffer.isEmpty()) {
                result.addFirst(buffer.pollLast());
            }
            return result;
        }, ArrayList::new);
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        return new FirstZipEntryLineSource(path).lines();
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamGZipFile(Path path) throws IOException {
        return new GZipLineSource(path).lines();
    }

    private static char findEndOfLine(RandomAccessFile randomAccessFile) throws IOException {
        char lineFeed = '\n';
        char carriageReturn = '\r';
        long currentPosition = randomAccessFile.length() - 1;

        while (currentPosition > 0) {
            randomAccessFile.seek(currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (character == lineFeed) {
                randomAccessFile.seek(currentPosition - 1);
                character = (char) randomAccessFile.readByte();
                return character == carriageReturn ? carriageReturn : lineFeed;
            }
            if (character == carriageReturn) {
                return carriageReturn;
            }
            currentPosition--;
        }
        return lineFeed;
    }

    private static int countLinesFromEnd(RandomAccessFile randomAccessFile, char eol, int numberOfLines) throws IOException {
        long currentPosition = randomAccessFile.length() - 1;
        int linesFound = 0;
        while (currentPosition > 0 && linesFound < numberOfLines) {
            randomAccessFile.seek(--currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (eol == character) {
                linesFound++;
            }
        }
        return linesFound;
    }

    private static void close(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            if (closeable == null) {
                continue;
            }
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Nothing useful to do while a stream is already closing.
            }
        }
    }

    private static final class ZipEntryLineSource implements AutoCloseable {

        private final ZipFile zipFile;
        private final BufferedReader reader;

        private ZipEntryLineSource(Path path, String entryName) throws IOException {
            zipFile = new ZipFile(path.toFile());
            try {
                ZipEntry entry = zipFile.getEntry(entryName);
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("Unable to read " + entryName + " from " + path);
                }
                reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
            } catch (IOException | RuntimeException ex) {
                close();
                throw ex;
            }
        }

        private Stream<String> lines() {
            return reader.lines().onClose(this::close);
        }

        @Override
        public void close() {
            GCLogSources.close(reader, zipFile);
        }
    }

    private static final class FirstZipEntryLineSource implements AutoCloseable {

        private final ZipInputStream zipStream;
        private final BufferedReader reader;

        private FirstZipEntryLineSource(Path path) throws IOException {
            zipStream = new ZipInputStream(Files.newInputStream(path));
            try {
                ZipEntry entry;
                do {
                    entry = zipStream.getNextEntry();
                } while (entry != null && entry.isDirectory());

                if (entry == null) {
                    throw new IOException("No readable entries found in " + path);
                }

                reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
            } catch (IOException | RuntimeException ex) {
                close();
                throw ex;
            }
        }

        private Stream<String> lines() {
            return reader.lines().onClose(this::close);
        }

        @Override
        public void close() {
            GCLogSources.close(reader, zipStream);
        }
    }

    private static final class GZipLineSource implements AutoCloseable {

        private final GZIPInputStream gzipStream;
        private final BufferedReader reader;

        private GZipLineSource(Path path) throws IOException {
            gzipStream = new GZIPInputStream(Files.newInputStream(path));
            try {
                reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
            } catch (RuntimeException ex) {
                close();
                throw ex;
            }
        }

        private Stream<String> lines() {
            return reader.lines().onClose(this::close);
        }

        @Override
        public void close() {
            GCLogSources.close(reader, gzipStream);
        }
    }
}
