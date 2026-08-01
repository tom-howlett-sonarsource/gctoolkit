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
 * Opens GC log sources for reading. The streams returned by this class hold the resources needed
 * to read the source, all of which are released when the returned stream is closed.
 */
public final class LogSourceStreams {

    private static final Logger LOG = Logger.getLogger(LogSourceStreams.class.getName());

    private static final char LF = '\n';
    private static final char CR = '\r';

    private LogSourceStreams() {
        // static utility
    }

    /**
     * Stream the lines of a log source, one line at a time. The source is read according to the
     * format discovered by {@link LogSourceFormat#of(Path)}. Only the first log in a compressed
     * source is read.
     *
     * @param path The path to the log source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be read, or is of a format that cannot be streamed.
     */
    public static Stream<String> lines(Path path) throws IOException {
        switch (LogSourceFormat.of(path)) {
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
     * Stream the lines of an uncompressed log.
     *
     * @param path The path to the log.
     * @return A stream of the lines in the log.
     * @throws IOException if the log cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the lines of the first log held in a Zip archive.
     *
     * @param path The path to the Zip archive.
     * @return A stream of the lines in the first log in the archive.
     * @throws IOException if the archive cannot be read.
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return linesOf(zipStream);
    }

    /**
     * Stream the lines of a named log held in a Zip archive.
     *
     * @param path      The path to the Zip archive.
     * @param entryName The name of the entry to be read.
     * @return A stream of the lines in the named entry.
     * @throws IOException if the archive cannot be read, or does not contain the named entry.
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException(path + " does not contain " + entryName);
            return linesOf(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException failed) {
            close(zipFile);
            throw failed;
        }
    }

    /**
     * Stream the lines of a GZip compressed log.
     *
     * @param path The path to the GZip compressed log.
     * @return A stream of the lines in the log.
     * @throws IOException if the log cannot be read.
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        return linesOf(new GZIPInputStream(Files.newInputStream(path)));
    }

    /**
     * Read the last lines of an uncompressed log. Fewer lines than were asked for are returned if
     * the log does not hold that many.
     * <p>
     * The read is a scan back through the bytes of the log counting line ends, which leaves two
     * long standing traits that callers rely on being unchanged. A log read all the way back to
     * its start gives up the first character of its first line, and a log written with CRLF line
     * ends gives up an empty first line. Neither matters to the callers, which look through the
     * lines for the last time stamp in the log.
     *
     * @param path          The path to the log.
     * @param numberOfLines The number of lines to read from the end of the log.
     * @return The last lines of the log, in the order in which they appear in the log.
     * @throws IOException if the log cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            long lastByte = LogSourceFiles.sizeInBytes(path) - 1;
            char eol = endOfLine(randomAccessFile, lastByte);

            long currentPosition = lastByte;
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
     * Find the character that the log uses to end a line by scanning back from the end of the log.
     * A carriage return is reported for logs that end their lines with a carriage return, with or
     * without a line feed.
     */
    private static char endOfLine(RandomAccessFile randomAccessFile, long lastByte) throws IOException {
        long currentPosition = lastByte;
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

    private static Stream<String> linesOf(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(inputStream)));
        return reader.lines().onClose(() -> close(reader));
    }

    /**
     * Closing a source that has been read is best effort. Reporting a failure to close would
     * replace whatever the caller was doing with the data with an error about the resource that
     * held it.
     */
    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to close log source", ioe);
        }
    }
}
