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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static java.util.stream.Collectors.toList;

/**
 * File system utilities shared by the modules that read GC log sources.
 * <p>
 * A GC log source is a path to either a plain text log, a ZIP or GZIP compressed log, or a
 * directory holding the segments of a rotating log. These utilities discover which of those a
 * path is, report how large it is, and open a stream of log lines from it.
 */
public final class GCLogSources {

    private static final Logger LOGGER = Logger.getLogger(GCLogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';

    private GCLogSources() {
    }

    /**
     * Discover the format of the source by looking for a directory or for the magic bytes of a
     * compressed file. A source that cannot be read is reported as {@link LogFileFormat#PLAINTEXT}.
     * @param path The path to the source.
     * @return The format of the source.
     */
    public static LogFileFormat formatOf(Path path) {
        if (Files.isDirectory(path))
            return LogFileFormat.DIRECTORY;
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2)
                return LogFileFormat.GZIP;
            if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2)
                return LogFileFormat.ZIP;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return the size of the source in bytes.
     * @param path The path to the source.
     * @return The number of bytes in the source.
     * @throws IOException Thrown if the size of the source cannot be read.
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a stream of the lines in the source, discovering the format of the source first.
     * The caller is responsible for closing the returned stream.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, formatOf(path));
    }

    /**
     * Open a stream of the lines in the source, reading it as the given format.
     * The caller is responsible for closing the returned stream.
     * @param path The path to the source.
     * @param format The format to read the source as.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read as the given format.
     */
    public static Stream<String> lines(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Return the names of the file entries in a ZIP compressed source. Directory entries are
     * not included.
     * @param path The path to the ZIP compressed source.
     * @return The names of the file entries, in the order they appear in the source.
     * @throws IOException Thrown if the source cannot be read as a ZIP file.
     */
    public static List<String> entryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }

    /**
     * Open a stream of the lines in a single entry of a ZIP compressed source.
     * The caller is responsible for closing the returned stream.
     * @param path The path to the ZIP compressed source.
     * @param entryName The name of the entry, as returned by {@link #entryNames(Path)}.
     * @return A stream of the lines in the entry.
     * @throws IOException Thrown if the source or the entry cannot be read.
     */
    public static Stream<String> entryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("Unable to find " + entryName + " in " + path);
            return linesOf(zipFile.getInputStream(entry)).onClose(() -> closeUnchecked(zipFile));
        } catch (IOException | RuntimeException failure) {
            closeSuppressing(zipFile, failure);
            throw failure;
        }
    }

    /**
     * Read the last lines of a plain text source. Fewer lines are returned if the source holds
     * fewer than the requested number of lines.
     * @param path The path to the source.
     * @param numberOfLines The maximum number of lines to read.
     * @return The last lines of the source, in the order they appear in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        long size = sizeInBytes(path);
        try (RandomAccessFile source = new RandomAccessFile(path.toFile(), "r")) {
            return lastLines(source, size, endOfLine(source, size), numberOfLines);
        }
    }

    /**
     * Find the character the source ends its lines with, so that lines can be counted backwards
     * from the end of the source.
     */
    private static char endOfLine(RandomAccessFile source, long size) throws IOException {
        long currentPosition = size - 1;
        while (currentPosition > 0) {
            source.seek(currentPosition);
            char character = (char) source.readByte();
            if (character == LINE_FEED) {
                source.seek(currentPosition - 1);
                return ((char) source.readByte() == CARRIAGE_RETURN) ? CARRIAGE_RETURN : LINE_FEED;
            } else if (character == CARRIAGE_RETURN) {
                return CARRIAGE_RETURN;
            }
            currentPosition--;
        }
        return 0;
    }

    /**
     * Seek back over the requested number of line endings and then read forward to the end of
     * the source.
     */
    private static List<String> lastLines(RandomAccessFile source, long size, char endOfLine, int numberOfLines)
            throws IOException {
        long currentPosition = size - 1;
        int linesFound = 0;
        while (currentPosition > 0 && linesFound < numberOfLines) {
            source.seek(--currentPosition);
            if (endOfLine == (char) source.readByte())
                linesFound++;
        }

        List<String> lines = new ArrayList<>();
        if (currentPosition == 0)
            source.seek(0); // the source holds fewer lines than were asked for, so read all of it
        else if (linesFound == 0)
            return lines;

        String line;
        while ((line = source.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }

    private static Stream<String> zipLines(Path path) throws IOException {
        InputStream source = Files.newInputStream(path);
        try {
            ZipInputStream zipStream = new ZipInputStream(source);
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return linesOf(zipStream);
        } catch (IOException | RuntimeException failure) {
            closeSuppressing(source, failure);
            throw failure;
        }
    }

    private static Stream<String> gzipLines(Path path) throws IOException {
        InputStream source = Files.newInputStream(path);
        try {
            return linesOf(new GZIPInputStream(source));
        } catch (IOException | RuntimeException failure) {
            closeSuppressing(source, failure);
            throw failure;
        }
    }

    private static Stream<String> linesOf(InputStream source) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(source)));
        return reader.lines().onClose(() -> closeUnchecked(reader));
    }

    private static void closeUnchecked(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private static void closeSuppressing(Closeable closeable, Exception failure) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            failure.addSuppressed(ioe);
        }
    }
}
