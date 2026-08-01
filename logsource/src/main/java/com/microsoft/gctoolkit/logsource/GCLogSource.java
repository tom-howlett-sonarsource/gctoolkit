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
 * Utilities for working with the files that GC logs are read from. A log source is either a
 * directory of log file segments, a plain text log file, or a Zip or GZip compressed log file.
 * <p>
 * These utilities are shared by all of the GCToolKit modules that read log files so that log
 * source discovery, sizing, and the opening of log streams behave the same way no matter which
 * module is reading the log.
 */
public final class GCLogSource {

    private static final Logger LOGGER = Logger.getLogger(GCLogSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSource() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Discover the format of the log source found at the given path. A source that cannot be
     * read, or that has no recognised compression header, is reported as
     * {@link LogFileFormat#PLAINTEXT}.
     *
     * @param path The path to the log source.
     * @return The discovered format, {@link LogFileFormat#UNKNOWN} if the path is {@code null}.
     */
    public static LogFileFormat discoverFormat(Path path) {
        if (path == null)
            return LogFileFormat.UNKNOWN;
        if (Files.isDirectory(path))
            return LogFileFormat.DIRECTORY;
        return magic(path);
    }

    /**
     * Return the size, in bytes, of the log source found at the given path.
     *
     * @param path The path to the log source.
     * @return The size of the source in bytes.
     * @throws IOException Thrown if the size of the source cannot be determined.
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a stream of the lines held in the log source found at the given path. The format of
     * the source is discovered before the source is opened.
     *
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> stream(Path path) throws IOException {
        return stream(path, discoverFormat(path));
    }

    /**
     * Open a stream of the lines held in the log source found at the given path.
     *
     * @param path The path to the log source.
     * @param format The format of the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the source cannot be read in the given format.
     */
    public static Stream<String> stream(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return streamPlainText(path);
            case ZIP:
                return streamZip(path);
            case GZIP:
                return streamGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a stream of the lines held in a plain text log file.
     *
     * @param path The path to the log file.
     * @return A stream of the lines in the log file.
     * @throws IOException Thrown if the file cannot be read.
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of the lines held in the first log file found in a Zip compressed file.
     *
     * @param path The path to the Zip file.
     * @return A stream of the lines in the first entry of the Zip file, an empty stream if the
     * Zip file holds no entries.
     * @throws IOException Thrown if the file cannot be read.
     */
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                zipStream.close();
                return Stream.empty();
            }
            return lines(new BufferedInputStream(zipStream), zipStream);
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipStream);
            throw e;
        }
    }

    /**
     * Open a stream of the lines held in a GZip compressed log file.
     *
     * @param path The path to the GZip file.
     * @return A stream of the lines in the GZip file.
     * @throws IOException Thrown if the file cannot be read.
     */
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return lines(new BufferedInputStream(gzipStream), gzipStream);
    }

    /**
     * Discover the names of the log files held in a Zip compressed file. Directory entries are
     * not included.
     *
     * @param path The path to the Zip file.
     * @return The names of the entries in the Zip file, in the order in which they are held.
     * @throws IOException Thrown if the file cannot be read.
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
     * Open a stream of the lines held in a named entry of a Zip compressed file.
     *
     * @param path The path to the Zip file.
     * @param entryName The name of the entry to be read, as returned by {@link #zipEntryNames(Path)}.
     * @return A stream of the lines in the entry.
     * @throws IOException Thrown if the file cannot be read or does not contain the entry.
     */
    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("Unable to find " + entryName + " in " + path);
            return lines(zipFile.getInputStream(entry), zipFile);
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipFile);
            throw e;
        }
    }

    /**
     * Read the last lines of a plain text log file.
     * <p>
     * The scan for the start of the tail runs backwards from the end of the file. If the file
     * holds fewer lines than were asked for, the scan runs off the front of the file and reading
     * starts on the second byte, which truncates the first character of the first line. This
     * behavior is retained from the implementation this method was extracted from.
     *
     * @param path The path to the log file.
     * @param numberOfLines The maximum number of lines to read from the end of the file.
     * @return The last lines of the file, an empty list if the file holds fewer than two lines.
     * @throws IOException Thrown if the file cannot be read.
     */
    // todo: implementation may be a bit ugly...
    // https://codereview.stackexchange.com/questions/79039/get-the-tail-of-a-file-the-last-10-lines
    // Tail is not a class, it's a method so the solution in stackoverflow isn't correct but the core
    // could be used here as it's cleaner
    public static List<String> tail(Path path, int numberOfLines) throws IOException {

        final char lineFeed = '\n';
        final char carriageReturn = '\r';
        boolean foundEOL = false;
        char eol = 0;
        long length = sizeInBytes(path);

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

    private static LogFileFormat magic(Path path) {
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

    private static Stream<String> lines(InputStream source, Closeable resource) {
        return new BufferedReader(new InputStreamReader(source)).lines()
                .onClose(() -> {
                    try {
                        resource.close();
                    } catch (IOException ioe) {
                        throw new UncheckedIOException(ioe);
                    }
                });
    }

    private static void closeQuietly(Closeable resource) {
        try {
            resource.close();
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
    }
}
