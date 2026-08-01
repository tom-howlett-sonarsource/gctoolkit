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
 * Reads the last lines of a GC log source.
 * <p>
 * An uncompressed source is read backwards from its end, so the cost of the read does not grow
 * with the size of the source. A source that can only be read forwards, such as an entry in a
 * ZIP archive, is instead collected through a fixed size window with {@link #lastElements(int)}.
 */
public final class LogFileTail {

    private static final char LF = '\n';
    private static final char CR = '\r';
    private static final char NO_EOL = 0;

    private LogFileTail() {
    }

    /**
     * Read the last lines of an uncompressed source by seeking backwards from the end of the file.
     *
     * @param path The path to the log source.
     * @param numberOfLines The number of lines wanted from the end of the source.
     * @return The last lines of the source, in the order they appear in it. The list is empty if
     * the source holds no line ending.
     * @throws IOException If the source cannot be read.
     */
    public static List<String> lastLines(Path path, int numberOfLines) throws IOException {
        List<String> lines = new ArrayList<>();
        long sizeInBytes = LogFileSources.sizeInBytes(path);
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            char eol = endOfLineCharacter(randomAccessFile, sizeInBytes);
            long currentPosition = sizeInBytes - 1;
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
     * Collect the last elements of a stream through a window of fixed size. Use this for sources
     * that can only be read forwards.
     *
     * @param numberOfElements The number of elements wanted from the end of the stream.
     * @param <T> The type of the elements in the stream.
     * @return A collector returning the last elements, in encounter order.
     */
    public static <T> Collector<T, ?, List<T>> lastElements(int numberOfElements) {
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, element) -> {
            if (buffer.size() == numberOfElements)
                buffer.pollFirst();
            buffer.add(element);
        }, (buffer, list) -> {
            while (list.size() < numberOfElements && !buffer.isEmpty()) {
                list.addFirst(buffer.pollLast());
            }
            return list;
        }, ArrayList::new);
    }

    /**
     * Work backwards from the end of the file looking for the first line ending, so that lines can
     * then be counted by looking for that one character.
     */
    private static char endOfLineCharacter(RandomAccessFile randomAccessFile, long sizeInBytes) throws IOException {
        long currentPosition = sizeInBytes - 1;
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
        return NO_EOL;
    }
}
