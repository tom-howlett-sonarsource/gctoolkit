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
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Common file-system operations for GC log sources.
 */
public final class GCLogFileSource {

    /** First byte of the GZIP file signature. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second byte of the GZIP file signature. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First byte of the ZIP file signature. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second byte of the ZIP file signature. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    /** Path backing this source. */
    private final Path path;
    /** Format discovered when the source was created. */
    private final Format format;

    /**
     * Creates a source for the supplied path and discovers its format.
     *
     * @param sourcePath path to a GC log source
     */
    public GCLogFileSource(final Path sourcePath) {
        this.path = Objects.requireNonNull(sourcePath, "path");
        this.format = discoverFormat(sourcePath);
    }

    /**
     * Returns the source path.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the discovered source format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the physical size of the source in bytes.
     *
     * @return physical byte size
     * @throws IOException if the size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Lists entries in a directory source. A regular source is returned as its
     * only entry.
     *
     * @return source entries
     * @throws IOException if the directory cannot be listed
     */
    public Stream<Path> entries() throws IOException {
        if (isDirectory()) {
            return Files.list(path);
        }
        if (Files.exists(path)) {
            return Stream.of(path);
        }
        return Stream.empty();
    }

    /**
     * Lists non-directory entries in a ZIP source.
     *
     * @return ZIP entry names
     * @throws IOException if the ZIP cannot be opened
     */
    public Stream<String> zipEntries() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        return zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .map(ZipEntry::getName)
                .onClose(() -> close(zipFile));
    }

    /**
     * Opens the source as a stream of lines. ZIP sources use the first
     * non-directory entry.
     * Closing the returned stream closes all underlying IO resources.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        InputStream input = open();
        Charset charset = Charset.defaultCharset();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new BufferedInputStream(input), charset));
        return reader.lines().onClose(() -> close(reader));
    }

    /**
     * Opens a named entry in a ZIP source as a stream of lines.
     * Closing the returned stream closes the ZIP file and entry stream.
     *
     * @param entryName ZIP entry name
     * @return entry lines
     * @throws IOException if the ZIP or entry cannot be opened
     */
    public Stream<String> lines(final String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            close(zipFile);
            throw new IOException(
                    "Unable to read " + entryName + " from " + path);
        }
        Charset charset = Charset.defaultCharset();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        zipFile.getInputStream(entry), charset));
        return reader.lines().onClose(() -> close(reader, zipFile));
    }

    /**
     * Returns whether this source is a ZIP archive.
     *
     * @return {@code true} for a ZIP source
     */
    public boolean isZip() {
        return format == Format.ZIP;
    }

    /**
     * Returns whether this source is a GZIP archive.
     *
     * @return {@code true} for a GZIP source
     */
    public boolean isGZip() {
        return format == Format.GZIP;
    }

    /**
     * Returns whether this source is plain text.
     *
     * @return {@code true} for a plain-text source
     */
    public boolean isPlainText() {
        return format == Format.PLAIN_TEXT;
    }

    /**
     * Returns whether this source is a directory.
     *
     * @return {@code true} for a directory source
     */
    public boolean isDirectory() {
        return format == Format.DIRECTORY;
    }

    private InputStream open() throws IOException {
        if (isPlainText()) {
            return Files.newInputStream(path);
        }
        if (isGZip()) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (isZip()) {
            ZipInputStream zipStream = new ZipInputStream(
                    Files.newInputStream(path));
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return zipStream;
        }
        throw new IOException("Unable to read " + path);
    }

    private static Format discoverFormat(final Path sourcePath) {
        if (Files.isDirectory(sourcePath)) {
            return Format.DIRECTORY;
        }
        if (!Files.isRegularFile(sourcePath)) {
            return Format.UNKNOWN;
        }
        try (InputStream input = Files.newInputStream(sourcePath)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        } catch (IOException ignored) {
            return Format.UNKNOWN;
        }
    }

    private static void close(final Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void close(final Closeable first, final Closeable second) {
        try {
            first.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            close(second);
        }
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        /** ZIP archive. */
        ZIP,
        /** GZIP archive. */
        GZIP,
        /** Plain-text file. */
        PLAIN_TEXT,
        /** File-system directory. */
        DIRECTORY,
        /** Missing, inaccessible, or unsupported source. */
        UNKNOWN
    }
}
