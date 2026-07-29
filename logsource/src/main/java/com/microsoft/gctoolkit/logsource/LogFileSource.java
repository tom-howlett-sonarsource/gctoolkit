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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A GC log source rooted in the file system. The format of the source is discovered from the
 * leading bytes of the file, which then determines how a stream of lines is opened over it.
 * Plain text, Zip and GZip logs are supported.
 */
public class LogFileSource {

    private static final Logger LOGGER = Logger.getLogger(LogFileSource.class.getName());

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;

    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';

    private final Path path;
    private final LogFileFormat format;

    /**
     * Discover the format of the source found at the given path.
     * @param path The path to the log, or to the directory containing the logs.
     */
    public LogFileSource(Path path) {
        this.path = path;
        this.format = discoverFormat(path);
    }

    /**
     * Determine the format of the source found at the given path. A source that cannot be read is
     * reported as {@link LogFileFormat#PLAINTEXT}.
     * @param path The path to the log, or to the directory containing the logs.
     * @return The discovered format.
     */
    public static LogFileFormat discoverFormat(Path path) {
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        else if (startsWith(path, GZIP_MAGIC_BYTE_1, GZIP_MAGIC_BYTE_2))
            return LogFileFormat.GZIP;
        else if (startsWith(path, ZIP_MAGIC_BYTE_1, ZIP_MAGIC_BYTE_2))
            return LogFileFormat.ZIP;
        else
            return LogFileFormat.PLAINTEXT;
    }

    private static boolean startsWith(Path path, int firstByte, int secondByte) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            return magicByteReader.read() == firstByte && magicByteReader.read() == secondByte;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
            return false;
        }
    }

    /**
     * Return the path to the source.
     * @return The path to the source.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the format of the source.
     * @return The format of the source.
     */
    public LogFileFormat getFormat() {
        return format;
    }

    /**
     * {@code true} if the source is a Zip compressed file.
     * @return {@code true} if the source is a Zip compressed file.
     */
    public boolean isZip() {
        return format == LogFileFormat.ZIP;
    }

    /**
     * {@code true} if the source is a GZip compressed file.
     * @return {@code true} if the source is a GZip compressed file.
     */
    public boolean isGZip() {
        return format == LogFileFormat.GZIP;
    }

    /**
     * {@code true} if the source is a regular file.
     * @return {@code true} if the source is a regular file.
     */
    public boolean isPlainText() {
        return format == LogFileFormat.PLAINTEXT;
    }

    /**
     * {@code true} if the source is a directory.
     * @return {@code true} if the source is a directory.
     */
    public boolean isDirectory() {
        return format == LogFileFormat.DIRECTORY;
    }

    /**
     * Return the size of the source, in bytes.
     * @return The number of bytes held by the source.
     * @throws IOException Thrown if the size of the source cannot be determined.
     */
    public long sizeInBytes() throws IOException {
        return Files.size(path);
    }

    /**
     * Open a stream of lines over the source. The caller owns the returned stream and should close
     * it in order to release the underlying file handles.
     * @return A stream of the lines held by the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public Stream<String> stream() throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamZipFile();
            case GZIP:
                return streamGZipFile();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * The first entry that is not a directory is the log of interest. Ownership of the open stream
     * passes to the returned stream of lines, which closes it.
     */
    private Stream<String> streamZipFile() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(zipStream);
        } catch (IOException ioe) {
            close(zipStream);
            throw ioe;
        }
    }

    private Stream<String> streamGZipFile() throws IOException {
        return lines(new GZIPInputStream(Files.newInputStream(path)));
    }

    private static Stream<String> lines(InputStream source) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(source), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Unable to close log source", ioe);
        }
    }

    /**
     * Read the last lines of the source. The source is read backwards from its end so that the
     * whole of a large log does not have to be scanned.
     * @param numberOfLines The maximum number of lines to read.
     * @return The last lines of the source, in the order in which they appear in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public List<String> tail(int numberOfLines) throws IOException {
        if (numberOfLines < 1)
            return Collections.emptyList();

        List<String> lines = new ArrayList<>();
        long sizeInBytes = sizeInBytes();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            randomAccessFile.seek(startOfLastLines(randomAccessFile, sizeInBytes, numberOfLines));
            String line;
            while ((line = randomAccessFile.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Scan backwards, counting line terminators, for the offset at which the last lines begin. The
     * offset is the start of the source when it holds fewer lines than were asked for.
     */
    private static long startOfLastLines(RandomAccessFile randomAccessFile, long sizeInBytes, int numberOfLines) throws IOException {
        long currentPosition = ignoreTrailingLineTerminator(randomAccessFile, sizeInBytes - 1);
        int linesFound = 0;
        while (currentPosition >= 0) {
            char character = characterAt(randomAccessFile, currentPosition);
            if (isEndOfLine(character)) {
                if (++linesFound == numberOfLines)
                    return currentPosition + 1;
                if (character == LINE_FEED && currentPosition > 0 && characterAt(randomAccessFile, currentPosition - 1) == CARRIAGE_RETURN)
                    currentPosition--;
            }
            currentPosition--;
        }
        return 0L;
    }

    /**
     * The terminator at the end of the source closes the last line rather than starting another one.
     */
    private static long ignoreTrailingLineTerminator(RandomAccessFile randomAccessFile, long position) throws IOException {
        if (position < 0)
            return position;
        char character = characterAt(randomAccessFile, position);
        if (!isEndOfLine(character))
            return position;
        if (character == LINE_FEED && position > 0 && characterAt(randomAccessFile, position - 1) == CARRIAGE_RETURN)
            return position - 2;
        return position - 1;
    }

    private static boolean isEndOfLine(char character) {
        return character == LINE_FEED || character == CARRIAGE_RETURN;
    }

    private static char characterAt(RandomAccessFile randomAccessFile, long position) throws IOException {
        randomAccessFile.seek(position);
        return (char) randomAccessFile.readByte();
    }
}
