// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * A file-system source for GC log data. The source format is discovered from
 * the file contents rather than its name.
 */
public final class LogFileSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final Format format;

    private LogFileSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discover a log source at the supplied path.
     *
     * @param path path to a plain, ZIP, or GZIP log, or to a directory
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static LogFileSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return new LogFileSource(path, discoverFormat(path));
    }

    /**
     * @return the source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * @return the format discovered from the source
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Return the physical size of the source in bytes.
     *
     * @return the source size
     * @throws IOException if the source size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Open the source as a lazily read stream of lines. For ZIP sources, the
     * first non-directory entry is opened.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
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
     * Open a named ZIP entry as a lazily read stream of lines.
     *
     * @param entryName ZIP entry name
     * @return entry lines
     * @throws IOException if this is not a ZIP source or the entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        if (format != Format.ZIP)
            throw new IOException(path + " is not a ZIP source");

        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory())
                throw new IOException("Unable to find ZIP entry " + entryName + " in " + path);
            BufferedReader reader = reader(zipFile.getInputStream(entry));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
    }

    /**
     * List the non-directory entries in a ZIP source.
     *
     * @return ZIP entry names
     * @throws IOException if this is not a ZIP source or the ZIP cannot be read
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP)
            throw new IOException(path + " is not a ZIP source");
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    private static Format discoverFormat(Path path) throws IOException {
        if (Files.isDirectory(path))
            return Format.DIRECTORY;

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2)
                return Format.GZIP;
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2)
                return Format.ZIP;
            return Format.PLAIN_TEXT;
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(zipInput);
        } catch (IOException exception) {
            zipInput.close();
            throw exception;
        }
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = reader(input);
        return reader.lines().onClose(() -> close(reader));
    }

    private static BufferedReader reader(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
    }

    private static void close(Closeable... closeables) {
        IOException failure = null;
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException exception) {
                if (failure == null)
                    failure = exception;
                else
                    failure.addSuppressed(exception);
            }
        }
        if (failure != null)
            throw new UncheckedIOException(failure);
    }

    /**
     * Supported log source formats.
     */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
