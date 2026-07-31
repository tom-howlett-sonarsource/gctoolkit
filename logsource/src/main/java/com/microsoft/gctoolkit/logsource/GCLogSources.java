// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
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
 * Utilities for working with the log sources that GCToolKit reads. A log source is a path which is
 * either a directory of log files, a plain text log file, or a log file compressed with Zip or GZip.
 * <p>
 * These utilities are shared so that every component reading a GC log discovers, sizes and opens
 * that log in the same way.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSources() {
        // utility class
    }

    /**
     * Determine the format of the log source by looking at the path and, for files, at the
     * leading magic bytes of the file.
     * @param path The path to the log source.
     * @return The format of the log source. A file whose magic bytes are not recognised is
     * reported as {@link LogFileFormat#PLAINTEXT}.
     */
    public static LogFileFormat discoverFormat(Path path) {
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        else
            return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return the size, in bytes, of the log source.
     * @param path The path to the log source.
     * @return The number of bytes in the log source, or {@code 0} if the size cannot be determined.
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
     * Return the names of the entries in a Zip file, ignoring directory entries.
     * @param path The path to the Zip file.
     * @return The names of the file entries in the Zip file, in the order in which they are listed.
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
     * Open the log source and stream it, one line at a time. The format of the source is discovered
     * from the source itself.
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the log source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, discoverFormat(path));
    }

    /**
     * Open the log source and stream it, one line at a time.
     * @param path The path to the log source.
     * @param format The format the log source is in.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the log source cannot be read, or the format cannot be streamed.
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
                throw new IOException("Unable to read " + path.toString());
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
     * Stream the first file entry in a Zip file, one line at a time.
     * @param path The path to the Zip file.
     * @return A stream of the lines in the first file entry of the Zip file.
     * @throws IOException Thrown if the Zip file cannot be read.
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return lines(zipStream);
    }

    /**
     * Stream a named entry in a Zip file, one line at a time.
     * @param path The path to the Zip file.
     * @param entryName The name of the entry to be streamed.
     * @return A stream of the lines in the named entry.
     * @throws IOException Thrown if the Zip file or the entry cannot be read.
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        return new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)))
                .lines()
                .onClose(() -> close(zipFile::close));
    }

    /**
     * Stream a GZip compressed log file, one line at a time.
     * @param path The path to the GZip file.
     * @return A stream of the lines in the GZip file.
     * @throws IOException Thrown if the GZip file cannot be read.
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        return lines(new GZIPInputStream(Files.newInputStream(path)));
    }

    /**
     * Read the last lines of a log file.
     * @param path The path to the log file.
     * @param numberOfLines The maximum number of lines to be read from the end of the file.
     * @return The last lines of the file, in the order in which they appear in the file.
     * @throws IOException Thrown if the log file cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {

        char lineFeed = '\n';
        char carriageReturn = '\r';
        boolean foundEOL = false;
        char eol = 0;
        long length = sizeInBytes(path);
        ArrayList<String> lines = new ArrayList<>();

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            long currentPosition = length - 1;
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

            currentPosition = length - 1;

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

    private static Stream<String> lines(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(inputStream)))
                .lines()
                .onClose(() -> close(inputStream::close));
    }

    private static boolean magic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    private static void close(Closer closer) {
        try {
            closer.close();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    @FunctionalInterface
    private interface Closer {
        void close() throws IOException;
    }
}
