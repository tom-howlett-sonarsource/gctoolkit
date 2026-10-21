// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
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
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering, sizing and opening GC log sources.
 */
public final class LogSources {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Determine the format of the source from its magic bytes.
     * @param path the source
     * @return the format; a file that is too short or unreadable is treated as plain text
     */
    public static LogSourceFormat format(Path path) {
        if (Files.isDirectory(path))
            return LogSourceFormat.DIRECTORY;
        try {
            if (size(path) >= 2) {
                try (InputStream magicByteReader = Files.newInputStream(path)) {
                    int magicByte1 = magicByteReader.read();
                    int magicByte2 = magicByteReader.read();
                    if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2)
                        return LogSourceFormat.GZIP;
                    if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2)
                        return LogSourceFormat.ZIP;
                }
            }
        } catch (IOException ignored) {
            // unreadable sources fall back to plain text; reading them will report the error
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * @param path the source
     * @return the size of the source in bytes
     * @throws IOException if the size cannot be read
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * List the files contained directly in a directory.
     * @param directory the directory to search
     * @return the paths of the entries in the directory
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> list(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    /**
     * @param path a ZIP file
     * @return the names of the non-directory entries, in archive order
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
     * Open the source as a stream of lines. Plain text is read as is, GZIP is decompressed, and
     * for ZIP the first non-directory entry is read. Closing the stream closes the source.
     * @param path the source
     * @param format the format of the source
     * @return the lines of the source
     * @throws IOException if the source cannot be opened or is a directory
     */
    public static Stream<String> lines(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            case ZIP:
                return lines(openFirstZipEntry(path));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a named entry of a ZIP file as a stream of lines. Closing the stream closes the archive.
     * @param path a ZIP file
     * @param entryName the entry to read
     * @return the lines of the entry
     * @throws IOException if the archive cannot be read or does not contain the entry
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException(entryName + " not found in " + path);
            return lines(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException e) {
            zipFile.close();
            throw e;
        }
    }

    private static InputStream openFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return zipStream;
        } catch (IOException | RuntimeException e) {
            zipStream.close();
            throw e;
        }
    }

    private static Stream<String> lines(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(inputStream)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
