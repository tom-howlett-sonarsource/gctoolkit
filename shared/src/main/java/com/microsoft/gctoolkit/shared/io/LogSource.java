// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A readable source within a plain, ZIP, or GZIP GC log input.
 *
 * <p>The streams returned by this class own every file and archive resource
 * used to produce them. Closing a line stream therefore closes the underlying
 * input and, for ZIP entries, the owning {@link ZipFile}.</p>
 */
public final class LogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_2 = 0x4b;
    /** Buffer used while counting uncompressed bytes. */
    private static final int SIZE_BUFFER_LENGTH = 8192;

    /** Filesystem input. */
    private final Path path;
    /** Input format. */
    private final Format format;
    /** Entry within a ZIP input, or {@code null}. */
    private final String entryName;
    /** Known uncompressed entry size, or a negative value. */
    private final long knownSize;

    private LogSource(final Path sourcePath,
                      final Format sourceFormat,
                      final String sourceEntryName,
                      final long sourceKnownSize) {
        this.path = Objects.requireNonNull(sourcePath);
        this.format = Objects.requireNonNull(sourceFormat);
        this.entryName = sourceEntryName;
        this.knownSize = sourceKnownSize;
    }

    /**
     * Represents the first readable source at {@code path}. ZIP inputs use the
     * first non-directory entry.
     *
     * @param path input file
     * @return a readable log source
     * @throws IOException if a directory or empty ZIP does not contain a source
     */
    public static LogSource first(final Path path) throws IOException {
        List<LogSource> sources = discover(path);
        if (sources.isEmpty()) {
            throw new IOException("No readable log source in " + path);
        }
        return sources.get(0);
    }

    /**
     * Discovers direct files in a directory, all non-directory ZIP entries, or
     * the single source represented by a plain or GZIP file.
     *
     * @param path input file or directory
     * @return sources in filesystem or archive encounter order
     * @throws IOException when the input cannot be inspected
     */
    public static List<LogSource> discover(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> paths = Files.list(path)) {
                return paths.filter(Files::isRegularFile)
                        .map(source -> new LogSource(
                                source, Format.PLAIN, null, -1L))
                        .collect(Collectors.toList());
            }
        }

        Format sourceFormat = detectFormat(path);
        if (sourceFormat != Format.ZIP) {
            return List.of(new LogSource(path, sourceFormat, null, -1L));
        }

        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            List<LogSource> sources = new ArrayList<>();
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> new LogSource(
                            path, Format.ZIP, entry.getName(), entry.getSize()))
                    .forEach(sources::add);
            return sources;
        }
    }

    /**
     * Detects compression by file magic rather than filename.
     *
     * @param path input file or directory
     * @return detected format
     */
    public static Format detectFormat(final Path path) {
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
        } catch (IOException ignored) {
            // Preserve the existing metadata behavior: unreadable paths are
            // treated as plain inputs and fail when a caller opens them.
        }
        return Format.PLAIN;
    }

    /**
     * Creates a source for a named ZIP entry.
     *
     * @param path ZIP file
     * @param entryName non-directory entry name
     * @return ZIP entry source
     * @throws IOException if the entry is absent or is a directory
     */
    public static LogSource zipEntry(final Path path, final String entryName)
            throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return new LogSource(path, Format.ZIP, entryName, entry.getSize());
        }
    }

    /**
     * Returns the filesystem input path.
     *
     * @return input path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the detected source format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the ZIP entry name, when this source is within a ZIP file.
     *
     * @return optional entry name
     */
    public Optional<String> entryName() {
        return Optional.ofNullable(entryName);
    }

    /**
     * Returns the uncompressed source size in bytes.
     *
     * @return uncompressed byte count
     * @throws IOException when the source cannot be read
     */
    public long byteSize() throws IOException {
        if (format == Format.PLAIN) {
            return Files.size(path);
        }
        if (format == Format.ZIP && knownSize >= 0L) {
            return knownSize;
        }
        try (InputStream input = open()) {
            byte[] buffer = new byte[SIZE_BUFFER_LENGTH];
            long size = 0L;
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
            }
            return size;
        }
    }

    /**
     * Opens the uncompressed bytes for this source.
     *
     * @return owned input stream
     * @throws IOException when the source cannot be opened
     */
    public InputStream open() throws IOException {
        if (format == Format.PLAIN) {
            return Files.newInputStream(path);
        }
        if (format == Format.GZIP) {
            InputStream input = Files.newInputStream(path);
            try {
                return new GZIPInputStream(input);
            } catch (IOException | RuntimeException exception) {
                input.close();
                throw exception;
            }
        }
        if (format == Format.ZIP) {
            return openZipEntry();
        }
        throw new IOException(
                "Unable to open directory as a log source: " + path);
    }

    /**
     * Opens the source as lines. Plain logs use UTF-8 and compressed logs use
     * the platform default charset, matching the prior callers' behavior.
     *
     * @return owned stream of lines
     * @throws IOException when the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        InputStream bufferedInput = new BufferedInputStream(open());
        InputStreamReader inputReader = format == Format.PLAIN
                ? new InputStreamReader(bufferedInput, StandardCharsets.UTF_8)
                : new InputStreamReader(bufferedInput);
        BufferedReader reader = new BufferedReader(inputReader);
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private InputStream openZipEntry() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return new ZipEntryInputStream(
                    zipFile.getInputStream(entry), zipFile);
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    /** Supported GC log source formats. */
    public enum Format {
        /** Uncompressed file. */
        PLAIN,
        /** ZIP archive entry. */
        ZIP,
        /** GZIP-compressed file. */
        GZIP,
        /** Filesystem directory. */
        DIRECTORY
    }

    private static final class ZipEntryInputStream extends FilterInputStream {
        /** ZIP file that owns the entry stream. */
        private final ZipFile zipFile;

        private ZipEntryInputStream(final InputStream input,
                                    final ZipFile owningZipFile) {
            super(input);
            this.zipFile = owningZipFile;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                zipFile.close();
            }
        }
    }
}
