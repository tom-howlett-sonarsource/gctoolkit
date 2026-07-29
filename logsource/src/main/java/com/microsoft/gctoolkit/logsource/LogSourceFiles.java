// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

/**
 * Discovery, sizing, and tail reading for GC log sources held in a file system.
 */
public final class LogSourceFiles {

    private static final Logger LOG = Logger.getLogger(LogSourceFiles.class.getName());

    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';
    private static final char NO_END_OF_LINE = 0;

    private LogSourceFiles() {
    }

    /**
     * Return the size, in bytes, of the log source found at the given path.
     * @param path The path to the log source.
     * @return The size of the source in bytes, {@code 0} if the size cannot be determined.
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return 0L;
        }
    }

    /**
     * Discover the sources held in a directory.
     * @param directory The path to the directory.
     * @return The paths of the entries in the directory.
     * @throws IOException Thrown if the directory cannot be read.
     */
    public static List<Path> list(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.collect(toList());
        }
    }

    /**
     * Discover the sources held in a Zip compressed file. Directory entries are not returned.
     * @param path The path to the Zip file.
     * @return The names of the file entries in the Zip file, in the order they are held.
     * @throws IOException Thrown if the Zip file cannot be read.
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }

    /**
     * Read the last lines of an uncompressed log source without reading the whole source.
     * @param path The path to the log source.
     * @param numberOfLines The maximum number of lines to read from the end of the source.
     * @return The last lines of the source, an empty list if the source holds no complete line.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        List<String> lines = new ArrayList<>();
        if (numberOfLines < 1)
            return lines;
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            char endOfLine = endOfLineCharacter(randomAccessFile);
            if (endOfLine == NO_END_OF_LINE)
                return lines;
            randomAccessFile.seek(startOfTail(randomAccessFile, endOfLine, numberOfLines));
            String line;
            while ((line = randomAccessFile.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Scan backwards from the end of the source, counting line ends, to find the offset of the
     * first of the last {@code numberOfLines} lines. The line end that terminates the source is
     * not counted, and the start of the source is returned when the source holds fewer lines than
     * are asked for.
     */
    private static long startOfTail(RandomAccessFile randomAccessFile, char endOfLine, int numberOfLines)
            throws IOException {
        // a carriage return is followed by a line feed, so a line end is two characters wide
        int endOfLineWidth = (endOfLine == CARRIAGE_RETURN) ? 2 : 1;
        int linesFound = 0;
        for (long position = randomAccessFile.length() - 1 - endOfLineWidth; position >= 0; position--) {
            randomAccessFile.seek(position);
            if (endOfLine == (char) randomAccessFile.readByte() && ++linesFound == numberOfLines)
                return position + endOfLineWidth;
        }
        return 0L;
    }

    /**
     * A {@link Collector} that retains only the last elements of a stream. Use this to read the
     * tail of a source that, unlike {@link #tail(Path, int)}, can only be read from the beginning.
     * @param numberOfLines The maximum number of elements to retain.
     * @param <T> The type of the elements in the stream.
     * @return A collector holding the last elements of the stream.
     */
    public static <T> Collector<T, ?, List<T>> tailCollector(int numberOfLines) {
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if (buffer.size() == numberOfLines)
                buffer.pollFirst();
            buffer.add(line);
        }, (buffer, list) -> {
            while (list.size() < numberOfLines && !buffer.isEmpty()) {
                list.addFirst(buffer.pollLast());
            }
            return list;
        }, ArrayList::new);
    }

    /**
     * Find the character the source uses to end a line by scanning backwards from the end of the
     * source. A source written on Windows ends a line with a carriage return followed by a line
     * feed, so the character before a line feed decides which of the two is used.
     */
    private static char endOfLineCharacter(RandomAccessFile randomAccessFile) throws IOException {
        long currentPosition = randomAccessFile.length() - 1;
        while (currentPosition > 0) {
            randomAccessFile.seek(currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (character == LINE_FEED) {
                randomAccessFile.seek(currentPosition - 1);
                return ((char) randomAccessFile.readByte() == CARRIAGE_RETURN) ? CARRIAGE_RETURN : LINE_FEED;
            }
            if (character == CARRIAGE_RETURN)
                return CARRIAGE_RETURN;
            currentPosition--;
        }
        return NO_END_OF_LINE;
    }
}
