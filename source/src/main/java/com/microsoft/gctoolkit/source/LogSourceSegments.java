// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

/**
 * Discovers and sizes GC log source segments.
 */
public final class LogSourceSegments {

    private static final char LF = '\n';
    private static final char CR = '\r';

    private LogSourceSegments() {}

    public static List<Path> listDirectory(Path path) throws IOException {
        try (Stream<Path> paths = Files.list(path)) {
            return paths.collect(toList());
        }
    }

    public static List<String> listZipEntries(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }

    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        if (numberOfLines < 1) {
            return List.of();
        }
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            if (randomAccessFile.length() == 0L) {
                return List.of();
            }
            char eol = findEndOfLine(randomAccessFile);
            if (eol == 0) {
                randomAccessFile.seek(0L);
            } else {
                seekToTail(randomAccessFile, eol, numberOfLines);
            }
            return readRemainingLines(randomAccessFile);
        }
    }

    public static <T> Collector<T, ?, List<T>> tail(int numberOfItems) {
        if (numberOfItems < 1) {
            return Collector.of(ArrayList::new, (buffer, line) -> buffer.clear(), (left, right) -> left);
        }
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if (buffer.size() == numberOfItems) {
                buffer.pollFirst();
            }
            buffer.add(line);
        }, (left, right) -> {
            right.forEach(item -> {
                if (left.size() == numberOfItems) {
                    left.pollFirst();
                }
                left.addLast(item);
            });
            return left;
        }, ArrayList::new);
    }

    private static char findEndOfLine(RandomAccessFile randomAccessFile) throws IOException {
        long currentPosition = randomAccessFile.length() - 1;
        while (currentPosition > 0) {
            randomAccessFile.seek(currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (character == LF) {
                return lineFeedOrCarriageReturn(randomAccessFile, currentPosition);
            } else if (character == CR) {
                return CR;
            }
            currentPosition--;
        }
        return 0;
    }

    private static char lineFeedOrCarriageReturn(RandomAccessFile randomAccessFile, long currentPosition) throws IOException {
        randomAccessFile.seek(currentPosition - 1);
        return (char) randomAccessFile.readByte() == CR ? CR : LF;
    }

    private static void seekToTail(RandomAccessFile randomAccessFile, char eol, int numberOfLines) throws IOException {
        long currentPosition = randomAccessFile.length() - 1;
        int linesFound = 0;
        while (currentPosition > 0 && linesFound < numberOfLines) {
            randomAccessFile.seek(--currentPosition);
            if (eol == (char) randomAccessFile.readByte()) {
                linesFound++;
            }
        }
    }

    private static List<String> readRemainingLines(RandomAccessFile randomAccessFile) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = randomAccessFile.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }
}
