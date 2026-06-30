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
 * Collectors for keeping only the end of a line stream.
 */
public final class TailLines {

    private static final int MAX_TAIL_BYTES = 1024 * 1024;

    private TailLines() {
    }

    /**
     * Keep only the last {@code lineCount} lines from the stream.
     *
     * @param lineCount maximum number of tail lines
     * @param <T> line type
     * @return collector containing tail lines in original order
     */
    public static <T> Collector<T, ?, List<T>> tail(int lineCount) {
        if (lineCount < 0) {
            throw new IllegalArgumentException("lineCount must be greater than or equal to zero");
        }
        return Collector.<T, Deque<T>, List<T>>of(
                ArrayDeque::new,
                (buffer, line) -> add(buffer, line, lineCount),
                (first, second) -> combine(first, second, lineCount),
                ArrayList::new);
    }

    /**
     * Read the last {@code lineCount} lines from a regular file.
     *
     * @param path source path
     * @param lineCount maximum number of tail lines
     * @return tail lines in original order
     * @throws IOException when the file cannot be read
     */
    public static List<String> from(Path path, int lineCount) throws IOException {
        if (lineCount < 0) {
            throw new IllegalArgumentException("lineCount must be greater than or equal to zero");
        }
        if (lineCount == 0) {
            return List.of();
        }

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            if (randomAccessFile.length() == 0) {
                return List.of();
            }

            char eol = findEndOfLine(randomAccessFile);
            long currentPosition = randomAccessFile.length() - 1;
            int linesFound = 0;
            while (currentPosition > 0 && linesFound < lineCount) {
                randomAccessFile.seek(--currentPosition);
                char character = (char) randomAccessFile.readByte();
                if (eol == character) {
                    linesFound++;
                }
            }

            ArrayList<String> lines = new ArrayList<>();
            if (linesFound > 0) {
                skipToBoundedTailWindow(randomAccessFile);
                String line;
                while ((line = randomAccessFile.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    private static void skipToBoundedTailWindow(RandomAccessFile randomAccessFile) throws IOException {
        long tailStart = Math.max(0, randomAccessFile.length() - MAX_TAIL_BYTES);
        if (randomAccessFile.getFilePointer() >= tailStart) {
            return;
        }

        randomAccessFile.seek(tailStart);
        if (tailStart > 0) {
            randomAccessFile.readLine();
        }
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
                if (character == carriageReturn) {
                    return carriageReturn;
                }
                return lineFeed;
            } else if (character == carriageReturn) {
                return carriageReturn;
            }
            currentPosition--;
        }
        return lineFeed;
    }

    private static <T> void add(Deque<T> buffer, T line, int lineCount) {
        if (lineCount == 0) {
            return;
        }
        if (buffer.size() == lineCount) {
            buffer.pollFirst();
        }
        buffer.addLast(line);
    }

    private static <T> Deque<T> combine(Deque<T> first, Deque<T> second, int lineCount) {
        second.forEach(line -> add(first, line, lineCount));
        return first;
    }
}
