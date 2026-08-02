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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Utilities for working with GC log sources. This is the single place in which GCToolKit
 * discovers the format of a log source, reports its size, and opens plain text, ZIP and
 * GZIP log files for reading.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSources() {
        // static utilities only
    }

    /**
     * Discover the format of the log source at the given path. Directories are reported as
     * {@link LogFileFormat#DIRECTORY}, compressed files are recognised by their magic bytes,
     * and everything else is assumed to be plain text.
     * @param path The path to the log source.
     * @return The discovered format, never {@code null}.
     */
    public static LogFileFormat discoverFormat(Path path) {
        if (path == null)
            return LogFileFormat.UNKNOWN;
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return the size, in bytes, of the log source.
     * @param path The path to the log source.
     * @return The size in bytes, or {@code 0} if the size cannot be determined.
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.size(path);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Unable to determine the size of " + path, e);
            return 0L;
        }
    }

    /**
     * Open the log source, discovering its format, and return a stream of the lines it contains.
     * @param path The path to the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException if the log source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, discoverFormat(path));
    }

    /**
     * Open the log source using the given format and return a stream of the lines it contains.
     * @param path The path to the log source.
     * @param format The format of the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException if the format is not one that can be streamed, or the source cannot be read.
     */
    public static Stream<String> lines(Path path, LogFileFormat format) throws IOException {
        if (format == LogFileFormat.PLAINTEXT)
            return plainTextLines(path);
        if (format == LogFileFormat.ZIP)
            return zipLines(path);
        if (format == LogFileFormat.GZIP)
            return gzipLines(path);
        throw new IOException("Unable to read " + path);
    }

    /**
     * Stream the lines of an uncompressed log file.
     * @param path The path to the log file.
     * @return A stream of the lines in the log file.
     * @throws IOException if the file cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the lines of the first, non directory entry in a ZIP file.
     * @param path The path to the ZIP file.
     * @return A stream of the lines in the first entry of the ZIP file.
     * @throws IOException if the file cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return lines(zipStream);
    }

    /**
     * Stream the lines of a named entry within a ZIP file.
     * @param path The path to the ZIP file.
     * @param entryName The name of the entry within the ZIP file.
     * @return A stream of the lines in the entry.
     * @throws IOException if the file or the entry cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        return new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry))).lines();
    }

    /**
     * Stream the lines of a GZIP compressed log file.
     * @param path The path to the GZIP file.
     * @return A stream of the lines in the file.
     * @throws IOException if the file cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> gzipLines(Path path) throws IOException {
        return lines(new GZIPInputStream(Files.newInputStream(path)));
    }

    /**
     * Return the names of the non directory entries in a ZIP file.
     * @param path The path to the ZIP file.
     * @return The entry names, in archive order, or an empty list if the file cannot be read.
     */
    public static List<String> zipEntryNames(Path path) {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * List the files found in a directory.
     * @param directory The directory to list.
     * @return The paths found in the directory.
     * @throws IOException if the directory cannot be listed.
     */
    public static List<Path> listFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.collect(Collectors.toList());
        }
    }

    /**
     * List the files in a directory whose file name starts with the given prefix. This is how
     * the segments of a rotating log file are discovered from any one of its segments.
     * @param directory The directory to list.
     * @param prefix The required file name prefix.
     * @return The matching paths.
     * @throws IOException if the directory cannot be listed.
     */
    public static List<Path> listFilesStartingWith(Path directory, String prefix) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(file -> file.getFileName().toString().startsWith(prefix))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Read the last lines of a plain text log file by seeking backwards from the end of the file.
     * @param path The path to the log file.
     * @param numberOfLines The maximum number of lines to read.
     * @return The trailing lines of the file, or an empty list if the file has fewer than
     * {@code numberOfLines} line endings.
     * @throws IOException if the file cannot be read.
     */
    public static List<String> tail(Path path, int numberOfLines) throws IOException {

        final char lf = '\n';
        final char cr = '\r';
        boolean foundEOL = false;
        char eol = 0;
        int linesFound = 0;

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
            long length = sizeInBytes(path);
            long currentPosition = length - 1;

            while (currentPosition > 0 && !foundEOL) {
                randomAccessFile.seek(currentPosition);
                char character = (char) randomAccessFile.readByte();
                if (character == lf) {
                    eol = lf;
                    randomAccessFile.seek(currentPosition - 1);
                    character = (char) randomAccessFile.readByte();
                    if (character == cr)
                        eol = cr;
                    foundEOL = true;
                } else if (character == cr) {
                    eol = cr;
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

    private static Stream<String> lines(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(inputStream))).lines();
    }
}
