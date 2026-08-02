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
 * Utilities used to discover the format of a GC log source, to measure it, and to open a
 * stream of the lines it contains. Plain text, Zip and GZip sources are supported.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private static final int MAGIC_LENGTH = 2;

    private GCLogSources() {
        // static utilities only.
    }

    /**
     * Discover the format of the log source by looking at the leading, magic bytes of the file.
     * A source that cannot be recognised as a directory, or as a compressed file, is reported
     * as {@link LogFileFormat#PLAINTEXT}.
     * @param path The path to the log source.
     * @return The format of the log source.
     */
    public static LogFileFormat formatOf(Path path) {
        if (Files.isDirectory(path))
            return LogFileFormat.DIRECTORY;
        if (sizeInBytes(path) < MAGIC_LENGTH)
            return LogFileFormat.PLAINTEXT;
        if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return the size, in bytes, of the log source. Sources that cannot be measured, such as
     * a directory or a file that cannot be read, are reported as being of zero length.
     * @param path The path to the log source.
     * @return The number of bytes in the log source, or {@code 0} if it cannot be measured.
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return 0L;
        }
    }

    /**
     * Open a stream of the lines in the log source. The format of the source is discovered
     * using {@link #formatOf(Path)}.
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, formatOf(path));
    }

    /**
     * Open a stream of the lines in the log source using the given format. In the case of a
     * Zip source, the first entry that is not a directory is streamed.
     * @param path The path to the log source.
     * @param format The format of the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the source cannot be read, or the format cannot be streamed.
     */
    public static Stream<String> lines(Path path, LogFileFormat format) throws IOException {
        if (format.isPlainText())
            return plainTextLines(path);
        else if (format.isZip())
            return firstZipEntryLines(path);
        else if (format.isGZip())
            return gzipLines(path);
        throw new IOException("Unable to read " + path.toString());
    }

    /**
     * Open a stream of the lines in an uncompressed log source.
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of the lines in the first entry of a Zip log source that is not a directory.
     * @param path The path to the log source.
     * @return A stream of the lines in the first entry of the log source.
     * @throws IOException Thrown if the source cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> firstZipEntryLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return lines(new BufferedInputStream(zipStream));
    }

    /**
     * Open a stream of the lines in a named entry of a Zip log source.
     * @param path The path to the log source.
     * @param entryName The name of the entry within the Zip file.
     * @return A stream of the lines in the named entry.
     * @throws IOException Thrown if the source, or the entry, cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        return lines(zipFile.getInputStream(entry));
    }

    /**
     * Return the names of the entries in a Zip log source, ignoring directory entries.
     * @param path The path to the log source.
     * @return The names of the entries in the Zip file, in the order in which they are held.
     * @throws IOException Thrown if the source cannot be read.
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
     * Open a stream of the lines in a GZip log source.
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the source cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> gzipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return lines(new BufferedInputStream(gzipStream));
    }

    /**
     * Return the last lines of an uncompressed log source. Fewer lines than were asked for are
     * returned when the source is shorter than the requested number of lines, in which case the
     * first byte of the source is consumed while searching backwards for a line ending.
     * @param path The path to the log source.
     * @param numberOfLines The number of lines to read from the end of the log source.
     * @return The last lines of the log source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {

        final char LF = '\n';
        final char CR = '\r';
        boolean foundEOL = false;
        char eol = 0;
        List<String> lines = new ArrayList<>();

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            long length = sizeInBytes(path);
            long currentPosition = length - 1;
            int linesFound = 0;

            while (currentPosition > 0 && !foundEOL) {
                randomAccessFile.seek(currentPosition);
                char character = (char) randomAccessFile.readByte();
                if (character == LF) {
                    eol = LF;
                    randomAccessFile.seek(currentPosition - 1);
                    character = (char) randomAccessFile.readByte();
                    if (character == CR)
                        eol = CR;
                    foundEOL = true;
                } else if (character == CR) {
                    eol = CR;
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
        return new BufferedReader(new InputStreamReader(inputStream)).lines();
    }

    private static boolean magic(Path path, int field1, int field2) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }
}
