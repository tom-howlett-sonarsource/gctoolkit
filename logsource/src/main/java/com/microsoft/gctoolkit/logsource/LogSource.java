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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static java.util.stream.Collectors.toList;

/**
 * A GC log source in the file system. The format of the source is discovered when the source is
 * created, and it determines how the source is read. This class is the single home for the IO
 * needed to size a log source and to open line streams over plain text, ZIP, and GZIP logs.
 */
public class LogSource {

    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';

    private final Path path;
    private final LogSourceFormat format;

    /**
     * Discover the format of the log source found at the given path.
     * @param path The path to the log source.
     */
    public LogSource(Path path) {
        this.path = path;
        this.format = LogSourceFormat.discover(path);
    }

    /**
     * Return the path to the log source.
     * @return The path to the log source.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the discovered format of the log source.
     * @return The format of the log source.
     */
    public LogSourceFormat getFormat() {
        return format;
    }

    /**
     * {@code true} if the log source is a directory.
     * @return {@code true} if the log source is a directory.
     */
    public boolean isDirectory() {
        return format == LogSourceFormat.DIRECTORY;
    }

    /**
     * {@code true} if the log source is a GZip compressed file.
     * @return {@code true} if the log source is a GZip compressed file.
     */
    public boolean isGZip() {
        return format == LogSourceFormat.GZIP;
    }

    /**
     * {@code true} if the log source is a regular file.
     * @return {@code true} if the log source is a regular file.
     */
    public boolean isPlainText() {
        return format == LogSourceFormat.PLAINTEXT;
    }

    /**
     * {@code true} if the log source is a Zip compressed file.
     * @return {@code true} if the log source is a Zip compressed file.
     */
    public boolean isZip() {
        return format == LogSourceFormat.ZIP;
    }

    /**
     * Return the size, in bytes, of the log source. For a compressed source this is the size of
     * the compressed file, not the size of the log it contains.
     * @return The number of bytes in the log source.
     * @throws IOException if the size of the source cannot be determined.
     */
    public long sizeInBytes() throws IOException {
        return Files.size(path);
    }

    /**
     * Stream the log source, one line at a time. A ZIP source is read from its first entry that is
     * not a directory.
     * @return A stream of the lines in the log source.
     * @throws IOException if the source cannot be read.
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return gzipLines();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Return the names of the entries in a ZIP log source, ignoring directory entries.
     * @return The names of the entries in the source, in the order they appear in the ZIP file.
     * @throws IOException if the source is not a readable ZIP file.
     */
    public List<String> zipEntryNames() throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }

    /**
     * Stream a single entry of a ZIP log source, one line at a time. The ZIP file is closed when
     * the returned stream is closed.
     * @param entryName The name of the entry to be streamed.
     * @return A stream of the lines in the entry.
     * @throws IOException if the source is not a readable ZIP file, or does not contain the entry.
     */
    public Stream<String> zipEntryLines(String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("Unable to find " + entryName + " in " + path);
            return lines(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException failed) {
            close(zipFile);
            throw failed;
        }
    }

    /**
     * Read the last lines of the log source. Only a plain text source, which does not have to be
     * read from the beginning to find its end, can be read this way.
     * @param numberOfLines The maximum number of lines to be read.
     * @return The last lines of the source, at most {@code numberOfLines} of them.
     * @throws IOException if the source cannot be read from the end.
     */
    public List<String> tail(int numberOfLines) throws IOException {
        if (!isPlainText())
            throw new IOException("Unable to read the last lines of a " + format + " source: " + path);
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile source = new RandomAccessFile(path.toFile(), "r")) {
            source.seek(startOfLastLines(source, numberOfLines));
            String line;
            while ((line = source.readLine()) != null)
                lines.add(line);
        }
        return lines;
    }

    /**
     * {@inheritDoc}
     * @return Returns {@code this.getPath().toString();}
     */
    @Override
    public String toString() {
        return path.toString();
    }

    /**
     * Scan backwards from the end of the source for the offset at which the last
     * {@code numberOfLines} lines begin. Sources with fewer lines than that are read in full.
     * @return The offset of the first byte of the last lines of the source.
     */
    private long startOfLastLines(RandomAccessFile source, int numberOfLines) throws IOException {
        long position = endOfFinalLine(source);
        int linesFound = 0;
        while (position > 0) {
            char character = characterAt(source, --position);
            if (isLineTerminator(character)) {
                if (++linesFound == numberOfLines)
                    return position + 1;
                if (character == LINE_FEED && position > 0 && characterAt(source, position - 1) == CARRIAGE_RETURN)
                    position--; // a carriage return followed by a line feed is a single line terminator
            }
        }
        return 0;
    }

    /**
     * The offset at which the final line of the source ends, ignoring the line terminator that
     * may follow it.
     */
    private long endOfFinalLine(RandomAccessFile source) throws IOException {
        long end = sizeInBytes();
        if (end > 0 && isLineTerminator(characterAt(source, end - 1))) {
            end--;
            if (end > 0 && characterAt(source, end) == LINE_FEED && characterAt(source, end - 1) == CARRIAGE_RETURN)
                end--;
        }
        return end;
    }

    private static boolean isLineTerminator(char character) {
        return character == LINE_FEED || character == CARRIAGE_RETURN;
    }

    private static char characterAt(RandomAccessFile source, long position) throws IOException {
        source.seek(position);
        return (char) source.readByte();
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return lines(zipStream);
    }

    private Stream<String> gzipLines() throws IOException {
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
            throw new UncheckedIOException(ioe);
        }
    }
}
