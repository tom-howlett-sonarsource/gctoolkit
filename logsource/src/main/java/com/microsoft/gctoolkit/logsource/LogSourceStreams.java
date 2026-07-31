// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Opens log sources as a stream of lines. Plain text, Zip and GZip sources are supported.
 * <p>
 * Every stream returned from this class releases the underlying resources when the stream is
 * closed, so callers should read them within a try-with-resources block.
 */
public final class LogSourceStreams {

    private static final Logger LOG = Logger.getLogger(LogSourceStreams.class.getName());

    private static final char LF = '\n';
    private static final char CR = '\r';

    private LogSourceStreams() {
        // Utility class.
    }

    /**
     * Stream the log source, one line at a time. The format of the source is discovered using
     * {@link LogSourceDiscovery#formatOf(Path)}.
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException If the log source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, LogSourceDiscovery.formatOf(path));
    }

    /**
     * Stream the log source, one line at a time, reading it as the given format.
     * @param path The path to the log source.
     * @param format The format of the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException If the log source cannot be read as the given format.
     */
    public static Stream<String> lines(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return plainTextLines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }

    /**
     * Stream an uncompressed log source, one line at a time.
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException If the log source cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the first file entry of a Zip compressed log source, one line at a time.
     * @param path The path to the Zip file.
     * @return A stream of the lines in the first file entry of the Zip file.
     * @throws IOException If the log source cannot be read.
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(new BufferedInputStream(zipStream));
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipStream);
            throw e;
        }
    }

    /**
     * Stream a named entry of a Zip compressed log source, one line at a time.
     * @param path The path to the Zip file.
     * @param entryName The name of the entry to be read.
     * @return A stream of the lines in the named entry.
     * @throws IOException If the entry cannot be found or cannot be read.
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("Unable to find " + entryName + " in " + path.toString());
            return lines(zipFile.getInputStream(entry)).onClose(() -> closeQuietly(zipFile));
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipFile);
            throw e;
        }
    }

    /**
     * Stream a GZip compressed log source, one line at a time.
     * @param path The path to the GZip file.
     * @return A stream of the lines in the log source.
     * @throws IOException If the log source cannot be read.
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        InputStream fileStream = Files.newInputStream(path);
        try {
            return lines(new BufferedInputStream(new GZIPInputStream(fileStream)));
        } catch (IOException | RuntimeException e) {
            closeQuietly(fileStream);
            throw e;
        }
    }

    /**
     * Read the last lines of an uncompressed log source. The whole log source is returned when it
     * holds fewer than {@code numberOfLines} lines.
     * @param path The path to the log source.
     * @param numberOfLines The number of lines to be read from the end of the log source.
     * @return The last lines of the log source.
     * @throws IOException If the log source cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            randomAccessFile.seek(startOfTail(randomAccessFile, LogSourceDiscovery.sizeInBytes(path), numberOfLines));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = randomAccessFile.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        }
    }

    /**
     * A collector that retains only the last {@code n} elements of a stream. This is the tail of
     * a log source that, unlike {@link #tail(Path, int)}, cannot be read backwards.
     * @param n The number of elements to retain.
     * @param <T> The type of the elements in the stream.
     * @return A collector returning the last {@code n} elements of the stream.
     */
    public static <T> Collector<T, ?, List<T>> lastLines(int n) {
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
     * Scan backwards from the end of the log source for the start of the requested number of
     * lines. The terminator of the last line is not counted. Returns the start of the log source
     * when it holds fewer lines than were asked for.
     */
    private static long startOfTail(RandomAccessFile randomAccessFile, long size, int numberOfLines) throws IOException {
        int linesFound = 0;
        long currentPosition = size - 1;
        while (currentPosition > 0) {
            currentPosition--;
            if (endsALine(randomAccessFile, currentPosition, size)) {
                if (++linesFound == numberOfLines)
                    return currentPosition + 1;
            }
        }
        return 0L;
    }

    /**
     * Return {@code true} if the byte at the given position terminates a line. The carriage return
     * of a {@code CRLF} pair is not a terminator in its own right, the line feed that follows it is.
     */
    private static boolean endsALine(RandomAccessFile randomAccessFile, long position, long size) throws IOException {
        randomAccessFile.seek(position);
        char character = (char) randomAccessFile.readByte();
        if (character == LF)
            return true;
        if (character != CR)
            return false;
        if (position + 1 == size)
            return true;
        return (char) randomAccessFile.readByte() != LF;
    }

    private static Stream<String> lines(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to close log source", ioe);
        }
    }
}
