// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collector;

/**
 * Shared tail operations for reading the most recent lines from GC log sources.
 */
public final class LogSourceTail {

    private LogSourceTail() {
    }

    public static List<String> readLastLines(Path path, int numberOfLines) throws IOException {
        if (numberOfLines <= 0) {
            return List.of();
        }
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            if (randomAccessFile.length() == 0) {
                return List.of();
            }
            return readLastLines(randomAccessFile, numberOfLines);
        }
    }

    public static <T> Collector<T, ?, List<T>> tail(int numberOfLines) {
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if (numberOfLines > 0) {
                if (buffer.size() == numberOfLines) {
                    buffer.pollFirst();
                }
                buffer.add(line);
            }
        }, (buffer, list) -> {
            while (list.size() < numberOfLines && !buffer.isEmpty()) {
                list.addFirst(buffer.pollLast());
            }
            return list;
        }, ArrayList::new);
    }

    private static List<String> readLastLines(RandomAccessFile randomAccessFile, int numberOfLines) throws IOException {
        EndOfLine endOfLine = findEndOfLine(randomAccessFile);
        long currentPosition = findTailStart(randomAccessFile, numberOfLines, endOfLine.character);

        if (!endOfLine.found || currentPosition == 0) {
            randomAccessFile.seek(0);
        }

        List<String> lines = new ArrayList<>();
        String line;
        while ((line = randomAccessFile.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }

    private static EndOfLine findEndOfLine(RandomAccessFile randomAccessFile) throws IOException {
        long currentPosition = randomAccessFile.length() - 1;
        while (currentPosition > 0) {
            randomAccessFile.seek(currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (character == '\n') {
                randomAccessFile.seek(currentPosition - 1);
                char endOfLine = (char) randomAccessFile.readByte() == '\r' ? '\r' : '\n';
                return new EndOfLine(endOfLine, true);
            }
            if (character == '\r') {
                return new EndOfLine('\r', true);
            }
            currentPosition--;
        }
        return new EndOfLine('\n', false);
    }

    private static long findTailStart(RandomAccessFile randomAccessFile, int numberOfLines, char endOfLine) throws IOException {
        long currentPosition = randomAccessFile.length() - 1;
        int linesFound = 0;
        while (currentPosition > 0 && linesFound < numberOfLines) {
            randomAccessFile.seek(--currentPosition);
            if (endOfLine == (char) randomAccessFile.readByte()) {
                linesFound++;
            }
        }
        return currentPosition;
    }

    private static class EndOfLine {

        private final char character;
        private final boolean found;

        EndOfLine(char character, boolean found) {
            this.character = character;
            this.found = found;
        }
    }
}
