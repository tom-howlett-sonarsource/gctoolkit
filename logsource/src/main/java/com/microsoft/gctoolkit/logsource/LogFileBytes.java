// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Byte level access to a GC log file. Sizing a log file in bytes allows its end to be read without
 * streaming the whole of what can be a very large file.
 */
public final class LogFileBytes {

    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';
    private static final char NO_END_OF_LINE = 0;

    private LogFileBytes() {}

    /**
     * Return the size of the log file in bytes.
     * @param path The path to the log file.
     * @return The number of bytes in the log file.
     * @throws IOException Thrown if the log file cannot be sized.
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Read the end of an uncompressed log file by seeking backwards from its last byte. The first
     * line returned may be a partial line when the log file holds fewer than the requested number
     * of lines. An empty list is returned when the log file holds no line terminator.
     * @param path The path to the log file.
     * @param numberOfLines The number of lines to read from the end of the log file.
     * @return The trailing lines of the log file, in the order they appear in the log file.
     * @throws IOException Thrown if the log file cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        List<String> lines = new ArrayList<>();
        long sizeInBytes = sizeInBytes(path);
        if (sizeInBytes < 1)
            return lines;

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            char endOfLine = endOfLineCharacter(randomAccessFile, sizeInBytes);
            long currentPosition = sizeInBytes - 1;
            int linesFound = 0;
            while (currentPosition > 0 && linesFound < numberOfLines) {
                randomAccessFile.seek(--currentPosition);
                if (endOfLine == (char) randomAccessFile.readByte())
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
     * Find the character that terminates the lines of the log file by seeking backwards from its
     * last byte to the first end of line it holds.
     */
    private static char endOfLineCharacter(RandomAccessFile randomAccessFile, long sizeInBytes) throws IOException {
        long currentPosition = sizeInBytes - 1;
        while (currentPosition > 0) {
            randomAccessFile.seek(currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (character == LINE_FEED) {
                randomAccessFile.seek(currentPosition - 1);
                return ((char) randomAccessFile.readByte() == CARRIAGE_RETURN) ? CARRIAGE_RETURN : LINE_FEED;
            } else if (character == CARRIAGE_RETURN) {
                return CARRIAGE_RETURN;
            }
            currentPosition--;
        }
        return NO_END_OF_LINE;
    }
}
