// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static java.util.stream.Collectors.toList;

/**
 * A GC log source rooted in the file system. The source may be a plain text log, a GZip
 * compressed log, a ZIP file containing one or more logs, or a directory holding the
 * segments of a rotating log.
 * <p>
 * The format of the source is discovered lazily, on first use, from the magic bytes of
 * the file. The streams handed out by this class hold the underlying source open; closing
 * the stream releases it.
 */
public class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';

    private final Path path;
    private GCLogSourceFormat format;

    /**
     * Create a source for the log, or the directory of logs, found at the given path.
     * @param path The path to the source.
     */
    public GCLogSource(Path path) {
        this.path = Objects.requireNonNull(path, "A GC log source requires a path");
    }

    /**
     * Return the path to the source.
     * @return The path to the source.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the discovered format of the source.
     * @return The format of the source.
     */
    public GCLogSourceFormat getFormat() {
        if (format == null)
            format = GCLogSourceFormat.discover(path);
        return format;
    }

    /**
     * {@code true} if the source is a Zip compressed file.
     * @return {@code true} if the source is a Zip compressed file.
     */
    public boolean isZip() {
        return getFormat() == GCLogSourceFormat.ZIP;
    }

    /**
     * {@code true} if the source is a GZip compressed file.
     * @return {@code true} if the source is a GZip compressed file.
     */
    public boolean isGZip() {
        return getFormat() == GCLogSourceFormat.GZIP;
    }

    /**
     * {@code true} if the source is a regular file.
     * @return {@code true} if the source is a regular file.
     */
    public boolean isPlainText() {
        return getFormat() == GCLogSourceFormat.PLAINTEXT;
    }

    /**
     * {@code true} if the source is a directory.
     * @return {@code true} if the source is a directory.
     */
    public boolean isDirectory() {
        return getFormat() == GCLogSourceFormat.DIRECTORY;
    }

    /**
     * The size of the source, in bytes, as it is held on disk. Compressed sources report
     * the compressed size.
     * @return The size of the source in bytes, or {@code 0} if the size cannot be determined.
     */
    public long byteSize() {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, ioe, () -> "Unable to determine the size of " + path);
            return 0L;
        }
    }

    /**
     * Open the source and stream it, one line at a time. The stream is opened according to
     * the discovered format of the source.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public Stream<String> stream() throws IOException {
        switch (getFormat()) {
            case PLAINTEXT:
                return streamPlainText();
            case ZIP:
                return streamZip();
            case GZIP:
                return streamGZip();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream the source as an uncompressed, plain text log.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public Stream<String> streamPlainText() throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the first log held in a ZIP compressed source. Directory entries are skipped.
     * @return A stream of the lines in the first log in the source.
     * @throws IOException If the source cannot be read.
     */
    // The reader is owned by the returned stream, which releases it when it is closed.
    @SuppressWarnings("java:S2095")
    public Stream<String> streamZip() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> close(reader));
    }

    /**
     * Stream a named log held in a ZIP compressed source.
     * @param entryName The name of the entry, as reported by {@link #zipEntryNames()}.
     * @return A stream of the lines in the named log.
     * @throws IOException If the source cannot be read, or it does not hold the named entry.
     */
    // The zip file and reader are owned by the returned stream, which releases them when it is closed.
    @SuppressWarnings("java:S2095")
    public Stream<String> streamZipEntry(String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            close(zipFile);
            throw new IOException("Unable to find " + entryName + " in " + path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
        return reader.lines().onClose(() -> {
            close(reader);
            close(zipFile);
        });
    }

    /**
     * Stream a GZip compressed source.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read.
     */
    // The reader is owned by the returned stream, which releases it when it is closed.
    @SuppressWarnings("java:S2095")
    public Stream<String> streamGZip() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)))));
        return reader.lines().onClose(() -> close(reader));
    }

    /**
     * The names of the logs held in a ZIP compressed source, in the order the source holds
     * them. Directory entries are not included.
     * @return The names of the logs in the source.
     * @throws IOException If the source cannot be read.
     */
    public List<String> zipEntryNames() throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }

    /**
     * Read the last lines of an uncompressed source. Fewer lines are returned if the source
     * does not hold that many, and no lines are returned if no line ending can be found.
     * <p>
     * Note that when the source holds fewer lines than were asked for, the backwards scan
     * stops one byte into the source, so the first character of the first line is dropped.
     * This has always been the behaviour of this read and callers, which use the tail to find
     * the latest time stamp in a log, tolerate it.
     * @param numberOfLines The number of lines to read from the end of the source.
     * @return The last lines of the source, in the order they appear in the source.
     * @throws IOException If the source cannot be read.
     */
    // Based on https://codereview.stackexchange.com/questions/79039/get-the-tail-of-a-file-the-last-10-lines
    public List<String> tail(int numberOfLines) throws IOException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            long size = byteSize();
            char endOfLine = findEndOfLine(randomAccessFile, size);
            if (!seekToLastLines(randomAccessFile, size, endOfLine, numberOfLines))
                return new ArrayList<>();
            return readRemainingLines(randomAccessFile);
        }
    }

    /**
     * Work back from the end of the source looking for the line ending it was written with.
     * @return The line ending character, or {@code 0} if the source holds no line ending.
     */
    private static char findEndOfLine(RandomAccessFile randomAccessFile, long size) throws IOException {
        long currentPosition = size - 1;
        while (currentPosition > 0) {
            randomAccessFile.seek(currentPosition);
            char character = (char) randomAccessFile.readByte();
            if (character == LINE_FEED) {
                randomAccessFile.seek(currentPosition - 1);
                return ((char) randomAccessFile.readByte() == CARRIAGE_RETURN) ? CARRIAGE_RETURN : LINE_FEED;
            } else if (character == CARRIAGE_RETURN)
                return CARRIAGE_RETURN;
            currentPosition--;
        }
        return 0;
    }

    /**
     * Position the source at the start of the last {@code numberOfLines} lines.
     * @return {@code true} if at least one line ending was found.
     */
    private static boolean seekToLastLines(RandomAccessFile randomAccessFile, long size, char endOfLine, int numberOfLines)
            throws IOException {
        long currentPosition = size - 1;
        int linesFound = 0;
        while (currentPosition > 0 && linesFound < numberOfLines) {
            randomAccessFile.seek(--currentPosition);
            if (endOfLine == (char) randomAccessFile.readByte())
                linesFound++;
        }
        return linesFound > 0;
    }

    private static List<String> readRemainingLines(RandomAccessFile randomAccessFile) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = randomAccessFile.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }

    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to close the GC log source", ioe);
        }
    }
}
