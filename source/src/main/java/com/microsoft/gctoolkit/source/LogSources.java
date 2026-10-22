// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering, sizing and opening GC log sources that may be plain text,
 * ZIP or GZIP compressed.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Determine the format of a source from its magic bytes. Unreadable files are treated as plain text.
     * @param path the source
     * @return the format of the source
     */
    public static LogFormat formatOf(Path path) {
        if (Files.isDirectory(path))
            return LogFormat.DIRECTORY;
        try (InputStream input = Files.newInputStream(path)) {
            int magic1 = input.read();
            int magic2 = input.read();
            if (magic1 == GZIP_MAGIC1 && magic2 == GZIP_MAGIC2)
                return LogFormat.GZIP;
            if (magic1 == ZIP_MAGIC1 && magic2 == ZIP_MAGIC2)
                return LogFormat.ZIP;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return LogFormat.PLAINTEXT;
    }

    /**
     * The size, in bytes, of the source as stored on disk.
     * @param path the source
     * @return the size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * List the entries of a directory whose file names match the filter.
     * @param directory the directory to search
     * @param fileNameFilter filter applied to each entry's file name
     * @return the matching paths
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> discover(Path directory, Predicate<String> fileNameFilter) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(entry -> fileNameFilter.test(entry.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * The names of the non-directory entries in a ZIP file, in archive order.
     * @param path the ZIP file
     * @return the entry names
     * @throws IOException if the ZIP file cannot be read
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
     * Open a source for reading. For ZIP files the first non-directory entry is read.
     * @param path the source
     * @return an input stream over the uncompressed content
     * @throws IOException if the source cannot be opened or is a directory
     */
    public static InputStream open(Path path) throws IOException {
        switch (formatOf(path)) {
            case PLAINTEXT:
                return Files.newInputStream(path);
            case GZIP:
                return openGZip(path);
            case ZIP:
                return openFirstZipEntry(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a single named entry of a ZIP file. Closing the stream closes the ZIP file.
     * @param path the ZIP file
     * @param entryName the entry to open
     * @return an input stream over the entry
     * @throws IOException if the file cannot be read or the entry does not exist
     */
    public static InputStream openZipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("No entry " + entryName + " in " + path);
            return closing(zipFile.getInputStream(entry), zipFile);
        } catch (IOException | RuntimeException e) {
            zipFile.close();
            throw e;
        }
    }

    /**
     * Read the lines of a source, see {@link #open(Path)}. The returned stream must be closed.
     * @param path the source
     * @return the lines of the source
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(open(path));
    }

    /**
     * Read the lines of an input stream. Closing the returned stream closes the input.
     * @param input the input
     * @return the lines of the input
     */
    public static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static InputStream openGZip(Path path) throws IOException {
        InputStream file = Files.newInputStream(path);
        try {
            return new GZIPInputStream(file);
        } catch (IOException | RuntimeException e) {
            file.close();
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

    private static InputStream closing(InputStream input, Closeable resource) {
        return new FilterInputStream(input) {
            @Override
            public void close() throws IOException {
                try (resource) {
                    super.close();
                }
            }
        };
    }
}
