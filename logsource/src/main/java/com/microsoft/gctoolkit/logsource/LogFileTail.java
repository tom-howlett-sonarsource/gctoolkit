// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collector;

/**
 * Reads the last lines of a GC log source. The end of a log is where the last, and therefore
 * the most recent, time stamp of a log segment is to be found.
 */
public final class LogFileTail {

    private static final char LF = '\n';
    private static final char CR = '\r';

    private LogFileTail() {
        // static utilities only
    }

    /**
     * Read the last lines of an uncompressed source. The source is read backwards from its
     * end, so the cost of the read is a function of the number of lines asked for and not of
     * the size of the source.
     * <p>
     * The backwards scan stops at the second byte of the source. A source that holds fewer
     * line endings than the number of lines asked for therefore yields all of its lines bar
     * the first character of the first of them, and a source that holds a single line, or no
     * line ending at all, yields no lines. Callers use this to find the last time stamp in a
     * log segment, for which reading a partial first line is harmless.
     *
     * @param path The path to the source.
     * @param numberOfLines The number of lines to be read from the end of the source.
     * @return The last lines of the source, in the order in which they appear in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static List<String> lastLines(Path path, int numberOfLines) throws IOException {
        List<String> lines = new ArrayList<>();
        long length = LogFileSources.sizeInBytes(path);
        if (length < 1)
            return lines;

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            char eol = endOfLineCharacter(randomAccessFile, length);
            long currentPosition = length - 1;
            int linesFound = 0;
            while (currentPosition > 0 && linesFound < numberOfLines) {
                randomAccessFile.seek(--currentPosition);
                if (eol == (char) randomAccessFile.readByte())
                    linesFound++;
            }

            if (linesFound > 0) {
                String line;
                while ((line = randomAccessFile.readLine()) != null) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /**
     * A collector that retains the last elements of a stream. Use this to take the tail of a
     * source that, unlike an uncompressed file, can only be read from the beginning.
     *
     * @param n The number of elements to be retained.
     * @param <T> The type of the elements in the stream.
     * @return A collector holding the last {@code n} elements of the stream.
     */
    public static <T> Collector<T, ?, List<T>> lastN(int n) {
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if (buffer.size() == n)
                buffer.pollFirst();
            buffer.add(line);
        }, (buffer, list) -> {
            while (list.size() < n && !buffer.isEmpty()) {
                list.addFirst(buffer.pollLast());
            }
            return list;
        }, ArrayList::new);
    }

    /**
     * Find the character that ends the lines of the source by looking for the first line
     * ending found when reading backwards from the end of the source.
     */
    private static char endOfLineCharacter(RandomAccessFile randomAccessFile, long length) throws IOException {
        long currentPosition = length - 1;
        while (currentPosition > 0) {
            randomAccessFile.seek(currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (character == LF) {
                randomAccessFile.seek(currentPosition - 1);
                return ((char) randomAccessFile.readByte() == CR) ? CR : LF;
            } else if (character == CR) {
                return CR;
            }
            currentPosition--;
        }
        return 0;
    }
}
