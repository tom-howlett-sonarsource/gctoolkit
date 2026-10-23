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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering, sizing and opening GC log sources.
 * Compressed sources are detected by their magic bytes, not by their file extension.
 */
public final class LogSources {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Determine the format of a log source. Unreadable files are reported as {@link LogFormat#PLAINTEXT}.
     * @param path the log source
     * @return the format of the source
     */
    public static LogFormat detectFormat(Path path) {
        if (Files.isDirectory(path))
            return LogFormat.DIRECTORY;
        try (InputStream in = Files.newInputStream(path)) {
            int magic1 = in.read();
            int magic2 = in.read();
            if (magic1 == GZIP_MAGIC1 && magic2 == GZIP_MAGIC2)
                return LogFormat.GZIP;
            if (magic1 == ZIP_MAGIC1 && magic2 == ZIP_MAGIC2)
                return LogFormat.ZIP;
        } catch (IOException ignored) {
            // fall through: treat as plain text, the subsequent read will report the problem
        }
        return LogFormat.PLAINTEXT;
    }

    /**
     * Size in bytes of the source as stored on disk. For a directory, the sum of the regular files it contains.
     * @param path the log source
     * @return the size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long size(Path path) throws IOException {
        if (!Files.isDirectory(path))
            return Files.size(path);
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).sum();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * List the entries of a directory, sorted by name.
     * @param directory the directory to list
     * @return the entries of the directory
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> listFiles(Path directory) throws IOException {
        return listFiles(directory, "");
    }

    /**
     * List the entries of a directory whose file name starts with {@code prefix}, sorted by name.
     * @param directory the directory to list
     * @param prefix the required file name prefix
     * @return the matching entries
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> listFiles(Path directory, String prefix) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open a log source as a stream of uncompressed bytes. For a ZIP, the first file entry is returned.
     * @param path the log source
     * @return an open stream which the caller must close
     * @throws IOException if the source is a directory or cannot be opened
     */
    public static InputStream open(Path path) throws IOException {
        switch (detectFormat(path)) {
            case GZIP:
                return new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)));
            case ZIP:
                return openFirstZipEntry(path);
            case PLAINTEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream the lines of a log source. The returned stream must be closed to release the file.
     * @param path the log source
     * @return the lines of the source
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path path) throws IOException {
        if (detectFormat(path) == LogFormat.PLAINTEXT)
            return Files.lines(path);
        return lines(open(path));
    }

    /**
     * The names of the file (non-directory) entries of a ZIP, in archive order.
     * @param zip the ZIP file
     * @return the entry names
     * @throws IOException if the ZIP cannot be read
     */
    public static List<String> zipEntryNames(Path zip) throws IOException {
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Stream the lines of a named ZIP entry. The returned stream must be closed to release the file.
     * @param zip the ZIP file
     * @param entryName the entry to read
     * @return the lines of the entry
     * @throws IOException if the ZIP cannot be read or the entry does not exist
     */
    public static Stream<String> zipEntryLines(Path zip, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(zip.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("No entry " + entryName + " in " + zip);
            return lines(zipFile.getInputStream(entry)).onClose(() -> closeQuietly(zipFile));
        } catch (IOException | RuntimeException e) {
            zipFile.close();
            throw e;
        }
    }

    private static InputStream openFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)));
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

    private static Stream<String> lines(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }
}
