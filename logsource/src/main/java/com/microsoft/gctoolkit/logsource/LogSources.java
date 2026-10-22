// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering, sizing and opening GC log sources that may be plain text,
 * ZIP or GZIP compressed. For compressed archives, the first non-directory entry is the log of interest.
 */
public final class LogSources {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Determine the format of a log source by inspecting its magic bytes.
     * @param path the path to the log source.
     * @return the format of the source. A file that cannot be read is reported as {@link LogSourceFormat#PLAINTEXT}.
     */
    public static LogSourceFormat formatOf(Path path) {
        if (Files.isDirectory(path))
            return LogSourceFormat.DIRECTORY;
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2)
                return LogSourceFormat.GZIP;
            if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2)
                return LogSourceFormat.ZIP;
        } catch (IOException ioe) {
            // unreadable sources fall through to plain text so that opening reports the real error
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * List the entries of a directory containing log files.
     * @param directory the directory to search.
     * @return the entries in the directory.
     * @throws IOException if the directory cannot be read.
     */
    public static List<Path> list(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.collect(Collectors.toList());
        }
    }

    /**
     * The size in bytes of a log source. For a directory, this is the sum of the sizes of the regular files it contains.
     * @param path the path to the log source.
     * @return the size in bytes.
     * @throws IOException if the size cannot be determined.
     */
    public static long sizeOf(Path path) throws IOException {
        if (!Files.isDirectory(path))
            return Files.size(path);
        long total = 0;
        for (Path entry : list(path)) {
            if (Files.isRegularFile(entry))
                total += Files.size(entry);
        }
        return total;
    }

    /**
     * Open the log source as an uncompressed stream of bytes.
     * @param path the path to a plain text, ZIP or GZIP log file.
     * @return an input stream which the caller must close.
     * @throws IOException if the source is a directory or cannot be opened.
     */
    public static InputStream open(Path path) throws IOException {
        LogSourceFormat format = formatOf(path);
        if (format == LogSourceFormat.DIRECTORY)
            throw new IOException("Unable to read directory " + path);
        InputStream input = Files.newInputStream(path);
        try {
            switch (format) {
                case GZIP:
                    return new GZIPInputStream(input);
                case ZIP:
                    return firstZipEntry(new ZipInputStream(input));
                default:
                    return input;
            }
        } catch (IOException | RuntimeException e) {
            input.close();
            throw e;
        }
    }

    /**
     * Stream the lines of a log source. Closing the stream closes the underlying file.
     * @param path the path to a plain text, ZIP or GZIP log file.
     * @return a stream of lines.
     * @throws IOException if the source cannot be opened.
     */
    public static Stream<String> lines(Path path) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(open(path)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static InputStream firstZipEntry(ZipInputStream zipStream) throws IOException {
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        // an archive without file entries yields an empty stream
        return zipStream;
    }
}
