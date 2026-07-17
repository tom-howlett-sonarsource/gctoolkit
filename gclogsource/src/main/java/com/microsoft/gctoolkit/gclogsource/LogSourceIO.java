// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSourceIO() {
    }

    public static LogSourceFormat detectFormat(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }

        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int magicByte1 = input.read();
            int magicByte2 = input.read();
            if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2) {
                return LogSourceFormat.GZIP;
            }
            if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2) {
                return LogSourceFormat.ZIP;
            }
            return LogSourceFormat.PLAINTEXT;
        }
    }

    public static Stream<String> stream(LogSourceFormat format, Path path) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamFirstZipEntry(path);
            case GZIP:
                return streamGZip(path);
            default:
                return Stream.empty();
        }
    }

    @SuppressWarnings("java:S2095")
    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            return Stream.empty();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
        return closeWithStream(reader, zipFile);
    }

    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    public static List<Path> list(Path path) throws IOException {
        try (Stream<Path> paths = Files.list(path)) {
            return paths.collect(Collectors.toList());
        }
    }

    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            return tail(randomAccessFile, numberOfLines);
        }
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry = zipStream.getNextEntry();
        while (entry != null && entry.isDirectory()) {
            entry = zipStream.getNextEntry();
        }
        if (entry == null) {
            zipStream.close();
            return Stream.empty();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return closeWithStream(reader);
    }

    private static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return closeWithStream(reader);
    }

    private static List<String> tail(RandomAccessFile randomAccessFile, int numberOfLines) throws IOException {
        char eol = findEndOfLine(randomAccessFile);
        int linesFound = countTailLines(randomAccessFile, numberOfLines, eol);
        return readTailLines(randomAccessFile, linesFound);
    }

    private static char findEndOfLine(RandomAccessFile randomAccessFile) throws IOException {
        long currentPosition = randomAccessFile.length() - 1;

        while (currentPosition > 0) {
            randomAccessFile.seek(currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (character == '\n') {
                randomAccessFile.seek(currentPosition - 1);
                character = (char) randomAccessFile.readByte();
                return character == '\r' ? '\r' : '\n';
            }
            if (character == '\r') {
                return '\r';
            }
            currentPosition--;
        }
        return 0;
    }

    private static int countTailLines(RandomAccessFile randomAccessFile, int numberOfLines, char eol) throws IOException {
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

    private static List<String> readTailLines(RandomAccessFile randomAccessFile, int linesFound) throws IOException {
        List<String> lines = new ArrayList<>();
        if (linesFound > 0) {
            String line;
            while ((line = randomAccessFile.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    @SuppressWarnings("java:S2095")
    private static Stream<String> closeWithStream(BufferedReader reader, Closeable... closeables) {
        return reader.lines().onClose(() -> {
            close(reader);
            close(closeables);
        });
    }

    private static void close(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Best effort close from Stream.onClose.
            }
        }
    }
}
