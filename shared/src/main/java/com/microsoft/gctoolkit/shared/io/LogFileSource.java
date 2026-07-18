// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * A readable source within a plain, ZIP, or GZIP GC log path.
 */
public final class LogFileSource {

    /** First byte in the GZIP signature. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second byte in the GZIP signature. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First byte in the ZIP signature. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second byte in the ZIP signature. */
    private static final int ZIP_MAGIC_2 = 0x4b;
    /** Buffer size used when counting decompressed bytes. */
    private static final int BUFFER_SIZE = 8192;

    /** Path containing this source. */
    private final Path path;
    /** ZIP entry name, or {@code null} for non-ZIP sources. */
    private final String entryName;
    /** Format of the containing path. */
    private final Format format;

    private LogFileSource(final Path sourcePath, final String sourceEntryName,
                          final Format sourceFormat) {
        this.path = Objects.requireNonNull(sourcePath);
        this.entryName = sourceEntryName;
        this.format = Objects.requireNonNull(sourceFormat);
    }

    /**
     * Creates the primary source represented by the path. For ZIP files this is
     * the first non-directory entry.
     *
     * @param path source path
     * @return primary log source
     * @throws IOException when the source cannot be inspected
     */
    public static LogFileSource from(final Path path) throws IOException {
        Format format = format(path);
        if (format != Format.ZIP) {
            return new LogFileSource(path, null, format);
        }

        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            String firstEntry = zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .findFirst()
                    .orElse(null);
            return new LogFileSource(path, firstEntry, format);
        }
    }

    /**
     * Discovers readable sources at the path. ZIP entries and direct directory
     * children are returned as individual sources.
     *
     * @param path source path
     * @return discovered sources in filesystem or archive order
     * @throws IOException when the source cannot be inspected
     */
    public static List<LogFileSource> discover(final Path path)
            throws IOException {
        Format format = format(path);
        if (format == Format.ZIP) {
            return discoverZipEntries(path);
        }
        if (format == Format.DIRECTORY) {
            return discoverDirectoryEntries(path);
        }
        return List.of(new LogFileSource(path, null, format));
    }

    /**
     * Lists the direct children of a directory without inspecting their
     * content.
     *
     * @param directory directory to inspect
     * @return direct children in filesystem order
     * @throws IOException when the directory cannot be listed
     */
    public static List<Path> children(final Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.collect(Collectors.toList());
        }
    }

    /**
     * Detects the source format from its path and leading bytes.
     *
     * @param path source path
     * @return detected format
     * @throws IOException when the source cannot be inspected
     */
    public static Format format(final Path path) throws IOException {
        return detectFormat(path);
    }

    /**
     * Returns the path containing this source.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the source name.
     *
     * @return ZIP entry name or path filename
     */
    public String name() {
        if (entryName != null) {
            return entryName;
        }
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    /**
     * Returns the format of the containing path.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the uncompressed byte size of this source.
     *
     * @return source size in bytes
     * @throws IOException when the size cannot be read
     */
    public long size() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.size(path);
        }
        if (format == Format.ZIP) {
            return zipEntrySize();
        }
        if (format == Format.GZIP) {
            try (InputStream input =
                         new GZIPInputStream(Files.newInputStream(path))) {
                return countBytes(input);
            }
        }
        return 0L;
    }

    /**
     * Opens this source as a stream of lines.
     *
     * @return line stream that owns the underlying IO resource
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(path);
        }
        if (format == Format.GZIP) {
            return lines(new GZIPInputStream(Files.newInputStream(path)));
        }
        if (format == Format.ZIP) {
            if (entryName == null) {
                return Stream.empty();
            }
            return lines(openZipEntry());
        }
        throw new IOException("Unable to read directory " + path);
    }

    private static Format detectFormat(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    private static List<LogFileSource> discoverZipEntries(final Path path)
            throws IOException {
        List<LogFileSource> sources = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .map(name -> new LogFileSource(path, name, Format.ZIP))
                    .forEach(sources::add);
        }
        return sources;
    }

    private static List<LogFileSource> discoverDirectoryEntries(final Path path)
            throws IOException {
        List<LogFileSource> sources = new ArrayList<>();
        try (Stream<Path> entries = Files.list(path)) {
            Iterable<Path> regularFiles = () ->
                    entries.filter(Files::isRegularFile).iterator();
            for (Path entry : regularFiles) {
                sources.add(from(entry));
            }
        }
        return sources;
    }

    private long zipEntrySize() throws IOException {
        if (entryName == null) {
            return 0L;
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            if (entry.getSize() >= 0L) {
                return entry.getSize();
            }
        }
        try (InputStream input = openZipEntry()) {
            return countBytes(input);
        }
    }

    private InputStream openZipEntry() throws IOException {
        if (entryName == null) {
            throw new IOException(
                    "ZIP file contains no readable entries: " + path);
        }
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null) {
            if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                return input;
            }
        }
        input.close();
        throw new IOException("ZIP entry not found: " + entryName);
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input),
                        Charset.defaultCharset()));
        return reader.lines().onClose(() -> close(reader));
    }

    private static long countBytes(final InputStream input) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long count = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            count += read;
        }
        return count;
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public enum Format {
        /** Uncompressed text file. */
        PLAIN_TEXT,
        /** ZIP archive. */
        ZIP,
        /** GZIP-compressed file. */
        GZIP,
        /** Filesystem directory. */
        DIRECTORY
    }
}
