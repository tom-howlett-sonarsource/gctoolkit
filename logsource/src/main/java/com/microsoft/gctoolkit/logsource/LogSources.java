// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities shared by the GCToolKit modules for working with the files a log may be sourced from.
 * The utilities cover discovering the format of a source, reporting its size in bytes, and opening
 * plain text, ZIP and GZIP sources as a stream of lines.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {
    }

    /**
     * Discover the format of the source found at the given path. Files are identified by their
     * magic bytes and not by their name.
     * @param path The path to the source.
     * @return The format of the source, {@link LogSourceFormat#PLAINTEXT} if it is not a directory
     * and holds neither the ZIP nor the GZIP magic bytes.
     */
    public static LogSourceFormat discover(Path path) {
        if (path.toFile().isDirectory())
            return LogSourceFormat.DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogSourceFormat.GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogSourceFormat.ZIP;
        else
            return LogSourceFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} if the first two bytes of the file match the given values.
     * @param path The path to the file.
     * @param field1 The expected value of the first byte.
     * @param field2 The expected value of the second byte.
     * @return {@code true} if both bytes match, {@code false} if they do not or the file cannot be read.
     */
    public static boolean magic(Path path, int field1, int field2) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Return the size, in bytes, of the source found at the given path.
     * @param path The path to the source.
     * @return The size of the source in bytes.
     * @throws IOException if the size cannot be determined.
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open the source found at the given path, discovering its format first.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> stream(Path path) throws IOException {
        return stream(path, discover(path));
    }

    /**
     * Open the source found at the given path using the given, previously discovered, format.
     * @param path The path to the source.
     * @param format The format of the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read, or the format cannot be streamed.
     */
    public static Stream<String> stream(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return streamPlainText(path);
            case ZIP:
                return streamZip(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }

    /**
     * Open an uncompressed source, one line at a time.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non directory entry of a ZIP compressed source, one line at a time.
     * @param path The path to the source.
     * @return A stream of the lines in the first entry of the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP compressed source, one line at a time.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read.
     */
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Read the last lines of an uncompressed source by reading backwards from the end of the file.
     * @param path The path to the source.
     * @param numberOfLines The number of lines to read.
     * @return The last lines of the source, an empty list if no line ending was found.
     * @throws IOException if the source cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {

        final char lineFeed = '\n';
        final char carriageReturn = '\r';
        boolean foundEOL = false;
        char eol = 0;
        List<String> lines = new ArrayList<>();

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            long currentPosition = sizeInBytes(path) - 1;
            int linesFound = 0;

            while (currentPosition > 0 && !foundEOL) {
                randomAccessFile.seek(currentPosition);
                char character = (char) randomAccessFile.readByte();
                if (character == lineFeed) {
                    eol = lineFeed;
                    randomAccessFile.seek(currentPosition - 1);
                    character = (char) randomAccessFile.readByte();
                    if (character == carriageReturn)
                        eol = carriageReturn;
                    foundEOL = true;
                } else if (character == carriageReturn) {
                    eol = carriageReturn;
                    foundEOL = true;
                } else
                    currentPosition--;
            }

            currentPosition = randomAccessFile.length() - 1;

            while (currentPosition > 0 && linesFound < numberOfLines) {
                randomAccessFile.seek(--currentPosition);
                char character = (char) randomAccessFile.readByte();
                if (eol == character)
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
     * A collector that retains the last {@code n} elements of a stream. Useful for reading the tail
     * of a source that, unlike an uncompressed file, can only be read from the beginning.
     * @param n The number of elements to retain.
     * @param <T> The type of the elements in the stream.
     * @return A collector holding the last {@code n} elements of the stream.
     */
    public static <T> Collector<T, ?, List<T>> tailCollector(int n) {
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
}
