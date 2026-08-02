// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Opens GC log sources for reading. Plain text, ZIP and GZIP sources are all read as a stream
 * of lines.
 */
public final class LogFileStreams {

    private LogFileStreams() {
    }

    /**
     * Stream the lines of the source, using the format derived from the source itself.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, LogFileFormat.of(path));
    }

    /**
     * Stream the lines of the source, reading it as the given format.
     * @param path The path to the source.
     * @param format The format of the source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read as the given format.
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
     * Stream the lines of a plain text source.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the lines of the first, non-directory entry of a ZIP source.
     * @param path The path to the ZIP file.
     * @return A stream of the lines in the first entry of the ZIP file.
     * @throws IOException Thrown if the ZIP file cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Stream the lines of a single, named entry within a ZIP source.
     * @param path The path to the ZIP file.
     * @param entryName The name of the entry within the ZIP file.
     * @return A stream of the lines in the entry.
     * @throws IOException Thrown if the ZIP file, or the entry, cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        return new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry))).lines();
    }

    /**
     * Stream the lines of every non-directory entry in a ZIP source, in the order in which the
     * entries appear in the ZIP file.
     * @param path The path to the ZIP file.
     * @return A stream of the lines in all of the entries of the ZIP file.
     * @throws IOException Thrown if the ZIP file, or one of its entries, cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> allZipEntryLines(Path path) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        List<ZipEntry> entries = zipFile.stream().filter(entry -> !entry.isDirectory()).collect(Collectors.toList());
        Vector<InputStream> streams = new Vector<>();

        try {
            entries
                    .stream()
                    .map(entry -> {
                        try {
                            return zipFile.getInputStream(entry);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEach(streams::add);
        } catch (UncheckedIOException uioe) {
            throw uioe.getCause();
        }

        SequenceInputStream sequenceInputStream = new SequenceInputStream(streams.elements());

        return new BufferedReader(new InputStreamReader(sequenceInputStream)).lines();
    }

    /**
     * Stream the lines of a GZIP source.
     * @param path The path to the GZIP file.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> gzipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    // todo: implementation may be a bit ugly...
    // https://codereview.stackexchange.com/questions/79039/get-the-tail-of-a-file-the-last-10-lines
    // Tail is not a class, it's a method so the solution in stackoverflow isn't correct but the core
    // could be used here as it's cleaner
    /**
     * Read the last lines of a plain text source. The source is read backwards, from the end of
     * the file, so the size of the source in bytes is used as the starting point.
     * @param path The path to the source.
     * @param numberOfLines The number of lines to be read from the end of the source.
     * @return The last lines of the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {

        char LF = '\n';
        char CR = '\r';
        boolean foundEOL = false;
        char eol = 0;
        ArrayList<String> lines = new ArrayList<>();
        long sizeInBytes = LogFileSources.byteSize(path);

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            long currentPosition = sizeInBytes - 1;
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
                } else if (character == CR && !foundEOL) {
                    eol = CR;
                    foundEOL = true;
                } else
                    currentPosition--;
            }

            currentPosition = sizeInBytes - 1;

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
}
