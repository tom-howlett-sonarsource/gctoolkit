// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering, sizing and opening GC log sources.
 * A ZIP or GZIP source is read through its first non-directory entry.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Discover the format of a log source from its magic bytes. A source that cannot be read
     * is reported as {@link LogSourceFormat#PLAINTEXT}.
     * @param path the log source
     * @return the format of the source
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
            LOG.warning(ioe.getMessage());
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * Size in bytes of a log source as stored on disk. For a directory, the sum of the sizes of
     * the regular files it directly contains.
     * @param path the log source
     * @return the size in bytes
     * @throws IOException if the source cannot be read
     */
    public static long sizeOf(Path path) throws IOException {
        if (!Files.isDirectory(path))
            return Files.size(path);
        try (Stream<Path> children = Files.list(path)) {
            return children.filter(Files::isRegularFile).mapToLong(LogSources::sizeOfFile).sum();
        }
    }

    private static long sizeOfFile(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Open the content of a plain, ZIP or GZIP log source.
     * @param path the log source
     * @return a stream of the (decompressed) content, which the caller must close
     * @throws IOException if the source is a directory, an empty ZIP, or cannot be read
     */
    public static InputStream open(Path path) throws IOException {
        LogSourceFormat format = formatOf(path);
        switch (format) {
            case PLAINTEXT:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openFirstZipEntry(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static InputStream openFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null)
                throw new IOException("No log file entry found in " + path);
            return zipStream;
        } catch (IOException ioe) {
            zipStream.close();
            throw ioe;
        }
    }

    /**
     * Stream the lines of a plain, ZIP or GZIP log source. Closing the returned stream closes the source.
     * @param path the log source
     * @return a stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path path) throws IOException {
        if (formatOf(path) == LogSourceFormat.PLAINTEXT)
            return Files.lines(path);
        return lines(open(path));
    }

    /**
     * List the names of the non-directory entries of a ZIP log source.
     * @param path the ZIP log source
     * @return the entry names in archive order
     * @throws IOException if the archive cannot be read
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Stream the lines of a named entry of a ZIP log source. Closing the returned stream closes the archive.
     * @param path the ZIP log source
     * @param entryName the name of the entry
     * @return a stream of lines
     * @throws IOException if the archive cannot be read or does not contain the entry
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException(entryName + " not found in " + path);
            return lines(zipFile.getInputStream(entry)).onClose(() -> closeQuietly(zipFile));
        } catch (IOException ioe) {
            zipFile.close();
            throw ioe;
        }
    }

    private static Stream<String> lines(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(inputStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            LOG.warning(e.getMessage());
        }
    }
}
