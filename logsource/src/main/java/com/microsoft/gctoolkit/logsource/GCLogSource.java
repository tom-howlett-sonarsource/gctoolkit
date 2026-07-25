// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * IO utilities for GC log sources. A log source is a plain text file, a Zip or GZip compressed file,
 * or a directory holding one or more GC log files.
 */
public final class GCLogSource {

    private static final Logger LOGGER = Logger.getLogger(GCLogSource.class.getName());

    /** The leading bytes of a GZip compressed file. */
    private static final int GZIP_MAGIC_NUMBER = 0x1F8B;

    /** The leading bytes of a Zip compressed file, "PK". */
    private static final int ZIP_MAGIC_NUMBER = 0x504B;

    /** The number of leading bytes needed to recognise a compressed log source. */
    private static final int MAGIC_NUMBER_BYTES = 2;

    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';

    private GCLogSource() {
    }

    /**
     * Determine the format of a log source from the file system entry it points at. A source that
     * cannot be read, or that is too short to hold a magic number, is reported as
     * {@link LogFileFormat#PLAINTEXT}.
     * @param path The path to the log source.
     * @return The format of the log source.
     */
    public static LogFileFormat discoverFormat(Path path) {
        if (Files.isDirectory(path))
            return LogFileFormat.DIRECTORY;
        if (sizeInBytes(path) < MAGIC_NUMBER_BYTES)
            return LogFileFormat.PLAINTEXT;
        switch (magicNumber(path)) {
            case GZIP_MAGIC_NUMBER:
                return LogFileFormat.GZIP;
            case ZIP_MAGIC_NUMBER:
                return LogFileFormat.ZIP;
            default:
                return LogFileFormat.PLAINTEXT;
        }
    }

    /**
     * Report the size, in bytes, of a log source. Sources that are not a regular file, such as a
     * directory or a path that does not exist, have no size that can be measured and report {@code 0}.
     * @param path The path to the log source.
     * @return The number of bytes in the log source, or {@code 0} if it cannot be measured.
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Unable to determine the size of " + path, ioe);
            return 0L;
        }
    }

    /**
     * Stream a log source, one line at a time, using the format discovered for it.
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the log source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, discoverFormat(path));
    }

    /**
     * Stream a log source, one line at a time, reading it as the given format. Formats that do not
     * resolve to a single stream of lines, such as {@link LogFileFormat#DIRECTORY}, are rejected.
     * @param path The path to the log source.
     * @param format The format to read the log source as.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the log source cannot be read as the given format.
     */
    public static Stream<String> lines(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return plainTextLines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream an uncompressed log file, one line at a time.
     * @param path The path to the log file.
     * @return A stream of the lines in the log file.
     * @throws IOException Thrown if the log file cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the first log file held in a Zip compressed file, one line at a time.
     * @param path The path to the Zip compressed file.
     * @return A stream of the lines in the first log file in the Zip compressed file.
     * @throws IOException Thrown if the Zip compressed file cannot be read.
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return readLines(new BufferedInputStream(zipStream));
    }

    /**
     * Stream a named log file held in a Zip compressed file, one line at a time.
     * @param path The path to the Zip compressed file.
     * @param entryName The name of the log file within the Zip compressed file.
     * @return A stream of the lines in the named log file.
     * @throws IOException Thrown if the Zip compressed file, or the named log file, cannot be read.
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("Unable to find " + entryName + " in " + path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
            return reader.lines().onClose(() -> closeAll(reader, zipFile));
        } catch (IOException ioe) {
            close(zipFile, ioe);
            throw ioe;
        }
    }

    /**
     * Stream a GZip compressed log file, one line at a time.
     * @param path The path to the GZip compressed file.
     * @return A stream of the lines in the GZip compressed file.
     * @throws IOException Thrown if the GZip compressed file cannot be read.
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        return readLines(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))));
    }

    /**
     * Read the trailing lines of a log file by seeking backwards from the end of it, so that the
     * cost of the read is a function of the number of lines wanted and not of the size of the file.
     * <p>
     * The seek counts line endings, and reads from the one that starts the requested tail, so the
     * first line returned may be empty: that happens when the seek stops at the start of the file
     * because it holds fewer than {@code numberOfLines} lines, and when the file is written with
     * {@code CRLF} line endings. A file with no line ending in it yields nothing. Callers therefore
     * get at least the last {@code numberOfLines - 1} lines of the file, and never more than
     * {@code numberOfLines}. An empty file yields nothing.
     * @param path The path to the log file.
     * @param numberOfLines The number of trailing lines wanted.
     * @return The trailing lines of the log file, in the order they appear in it.
     * @throws IOException Thrown if the log file cannot be opened for reading, which includes the
     * case where {@code path} is a directory.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        boolean foundEOL = false;
        char eol = 0;
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            long currentPosition = randomAccessFile.length() - 1;
            int linesFound = 0;

            while (currentPosition > 0 && !foundEOL) {
                randomAccessFile.seek(currentPosition);
                char character = (char) randomAccessFile.readByte();
                if (character == LINE_FEED) {
                    eol = LINE_FEED;
                    randomAccessFile.seek(currentPosition - 1);
                    character = (char) randomAccessFile.readByte();
                    if (character == CARRIAGE_RETURN)
                        eol = CARRIAGE_RETURN;
                    foundEOL = true;
                } else if (character == CARRIAGE_RETURN) {
                    eol = CARRIAGE_RETURN;
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

            List<String> lines = new ArrayList<>();
            if (linesFound > 0) {
                String line;
                while ((line = randomAccessFile.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    private static Stream<String> readLines(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines().onClose(() -> closeAll(reader));
    }

    /**
     * Close every resource backing a stream of lines, reporting the failure the way
     * {@link Files#lines(Path)} does.
     */
    private static void closeAll(AutoCloseable... closeables) {
        IOException failure = null;
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception e) {
                if (failure == null)
                    failure = new IOException("Unable to close a log source", e);
                else
                    failure.addSuppressed(e);
            }
        }
        if (failure != null)
            throw new UncheckedIOException(failure);
    }

    private static void close(AutoCloseable closeable, IOException failure) {
        try {
            closeable.close();
        } catch (Exception e) {
            failure.addSuppressed(e);
        }
    }

    /**
     * Read the leading bytes that identify a compressed log source. The source is known to hold at
     * least {@link #MAGIC_NUMBER_BYTES} bytes by the time this is called.
     */
    private static int magicNumber(Path path) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int first = magicByteReader.read();
            int second = magicByteReader.read();
            return (first << 8) | second;
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Unable to read the leading bytes of " + path, ioe);
            return 0;
        }
    }
}
