// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collector;

/**
 * Reads the tail (the last few lines) of a GC log file. The end of a log is
 * used to work out how far through its run a segment reaches, which in turn
 * orders rotating log segments. The logic was previously duplicated, once as a
 * {@link Collector} for streamed content and once as a random-access read of a
 * file on disk; both live here now.
 */
public final class GCLogFileTail {

    private GCLogFileTail() {
    }

    /**
     * A {@link Collector} that retains only the last {@code n} elements of a stream,
     * preserving their encounter order. Useful when the source is already streamed
     * (for example, from inside a Zip archive) and cannot be read backwards.
     * @param n the number of trailing elements to keep.
     * @param <T> the element type.
     * @return a collector yielding the last {@code n} elements.
     * @throws IllegalArgumentException if {@code n} is negative.
     */
    public static <T> Collector<T, ?, List<T>> collector(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative");
        }
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, element) -> {
            if (buffer.size() == n) {
                buffer.pollFirst();
            }
            buffer.add(element);
        }, (buffer, tail) -> {
            while (tail.size() < n && !buffer.isEmpty()) {
                tail.addFirst(buffer.pollLast());
            }
            return tail;
        }, ArrayList::new);
    }

    /**
     * Read the last {@code numberOfLines} lines of a file directly from disk,
     * seeking backwards from the end so the whole file does not have to be read.
     * @param path the path to the file.
     * @param numberOfLines the number of trailing lines to read.
     * @return the trailing lines, in file order. May contain fewer than
     * {@code numberOfLines} lines if the file is shorter.
     * @throws IOException if the file cannot be read.
     * @throws IllegalArgumentException if {@code numberOfLines} is negative.
     */
    public static List<String> read(Path path, int numberOfLines) throws IOException {
        if (numberOfLines < 0) {
            throw new IllegalArgumentException("numberOfLines must not be negative");
        }

        List<String> lines = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long length = file.length();
            if (length == 0 || numberOfLines == 0) {
                return lines;
            }

            char endOfLine = lineEnding(file, length);
            long readFrom = 0;
            int linesFound = 0;
            long currentPosition = length - 1;
            while (currentPosition > 0 && linesFound < numberOfLines) {
                file.seek(--currentPosition);
                if (endOfLine == (char) file.readByte()) {
                    linesFound++;
                    if (linesFound == numberOfLines) {
                        // Start of the n-th line from the end, just past its delimiter.
                        readFrom = currentPosition + 1;
                    }
                }
            }

            file.seek(readFrom);
            String line;
            while ((line = file.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Find the byte that terminates each line by scanning backwards from the end
     * of the file. This is the last byte of the line separator, so it is
     * {@code '\n'} for both Unix ({@code \n}) and Windows ({@code \r\n}) logs, and
     * {@code '\r'} for classic Mac ({@code \r}) logs. Returns {@code 0} if the file
     * contains no line ending at all.
     */
    private static char lineEnding(RandomAccessFile file, long length) throws IOException {
        long position = length - 1;
        while (position > 0) {
            file.seek(position);
            char character = (char) file.readByte();
            if (character == '\n' || character == '\r') {
                return character;
            }
            position--;
        }
        return 0;
    }
}
