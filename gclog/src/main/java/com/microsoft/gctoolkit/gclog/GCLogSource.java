// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A logical GC log source backed by a file or an entry in a ZIP file.
 */
public final class GCLogSource {

    private static final int BUFFER_SIZE = 8192;
    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;
    private static final long UNKNOWN_SIZE = -1L;

    private final Path path;
    private final String entryName;
    private final Format format;
    private final long contentSize;

    private GCLogSource(Path path, String entryName, Format format, long contentSize) {
        this.path = Objects.requireNonNull(path);
        this.entryName = entryName;
        this.format = Objects.requireNonNull(format);
        this.contentSize = contentSize;
    }

    /**
     * Finds the logical log sources represented by a file or directory. ZIP files
     * contribute one source for every non-directory entry.
     *
     * @param path file or directory to inspect
     * @return discovered sources in filesystem or ZIP entry order
     * @throws IOException if the path cannot be inspected
     */
    public static List<GCLogSource> discover(Path path) throws IOException {
        Format sourceFormat = formatOf(path);
        if (sourceFormat == Format.DIRECTORY) {
            return discoverDirectory(path);
        }
        if (sourceFormat == Format.ZIP) {
            return discoverZip(path);
        }
        long size = sourceFormat == Format.PLAIN_TEXT ? Files.size(path) : UNKNOWN_SIZE;
        return List.of(new GCLogSource(path, null, sourceFormat, size));
    }

    /**
     * Lists regular files directly contained in a directory without opening them.
     *
     * @param directory directory to inspect
     * @return regular file paths in filesystem order
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> filesIn(Path directory) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    /**
     * Returns the first logical log source represented by a file or directory.
     *
     * @param path file or directory to inspect
     * @return first discovered source
     * @throws IOException if no source can be found or the path cannot be inspected
     */
    public static GCLogSource first(Path path) throws IOException {
        return discover(path).stream()
                .findFirst()
                .orElseThrow(() -> new IOException("No log source found in " + path));
    }

    /**
     * Creates a source for a named entry in a ZIP file.
     *
     * @param path path to the ZIP file
     * @param entryName entry to read
     * @return a source for the requested entry
     * @throws IOException if the entry cannot be found
     */
    public static GCLogSource zipEntry(Path path, String entryName) throws IOException {
        Objects.requireNonNull(entryName);
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return new GCLogSource(path, entryName, Format.ZIP, entry.getSize());
        }
    }

    /**
     * Detects the physical source format using its leading magic bytes.
     *
     * @param path path to inspect
     * @return detected format
     * @throws IOException if the path cannot be inspected
     */
    public static Format formatOf(Path path) throws IOException {
        Objects.requireNonNull(path);
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1 && secondByte == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1 && secondByte == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Opens the uncompressed content represented by this source.
     *
     * @return content stream; closing it also closes any backing archive
     * @throws IOException if the content cannot be opened
     */
    public InputStream open() throws IOException {
        switch (format) {
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openZipEntry();
            case PLAIN_TEXT:
                return Files.newInputStream(path);
            default:
                throw new IOException("Unable to open directory as a log source: " + path);
        }
    }

    /**
     * Streams the source as lines using UTF-8 for plain files and the platform
     * default charset for compressed files, matching the historical behavior.
     *
     * @return lazily read lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        LineSpliterator lines = new LineSpliterator(this);
        return StreamSupport.stream(lines, false).onClose(lines::close);
    }

    /**
     * Returns the number of uncompressed bytes produced by {@link #open()}.
     *
     * @return uncompressed content size
     * @throws IOException if the content must be read and cannot be opened
     */
    public long size() throws IOException {
        return contentSize == UNKNOWN_SIZE ? countBytes() : contentSize;
    }

    /**
     * @return physical path containing this source
     */
    public Path path() {
        return path;
    }

    /**
     * @return ZIP entry name, or the physical filename for a plain or GZIP source
     */
    public String name() {
        if (entryName != null) {
            return entryName;
        }
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    /**
     * @return detected source format
     */
    public Format format() {
        return format;
    }

    private static List<GCLogSource> discoverDirectory(Path directory) throws IOException {
        List<GCLogSource> sources = new ArrayList<>();
        for (Path child : filesIn(directory)) {
            sources.addAll(discover(child));
        }
        return List.copyOf(sources);
    }

    private static List<GCLogSource> discoverZip(Path path) throws IOException {
        List<GCLogSource> sources = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> new GCLogSource(path, entry.getName(), Format.ZIP, entry.getSize()))
                    .forEach(sources::add);
        }
        return List.copyOf(sources);
    }

    private InputStream openZipEntry() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return new ZipEntryInputStream(zipFile.getInputStream(entry), zipFile);
        } catch (IOException | RuntimeException exception) {
            try {
                zipFile.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private long countBytes() throws IOException {
        long count = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = open()) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                count += bytesRead;
            }
        }
        return count;
    }

    private BufferedReader openReader() throws IOException {
        Charset charset = format == Format.PLAIN_TEXT
                ? StandardCharsets.UTF_8
                : Charset.defaultCharset();
        return new BufferedReader(
                new InputStreamReader(new BufferedInputStream(open()), charset));
    }

    /**
     * Physical representation of a discovered source.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    private static final class LineSpliterator
            extends Spliterators.AbstractSpliterator<String> implements AutoCloseable {

        private final BufferedReader reader;
        private boolean closed;

        private LineSpliterator(GCLogSource source) throws IOException {
            super(Long.MAX_VALUE, ORDERED | NONNULL);
            reader = source.openReader();
        }

        @Override
        public boolean tryAdvance(Consumer<? super String> action) {
            if (closed) {
                return false;
            }
            try {
                String line = reader.readLine();
                if (line == null) {
                    close();
                    return false;
                }
                action.accept(line);
                return true;
            } catch (IOException exception) {
                closeAfter(exception);
                throw new UncheckedIOException(exception);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                reader.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void closeAfter(IOException failure) {
            closed = true;
            try {
                reader.close();
            } catch (IOException closeException) {
                failure.addSuppressed(closeException);
            }
        }
    }

    private static final class ZipEntryInputStream extends FilterInputStream {

        private final ZipFile zipFile;

        private ZipEntryInputStream(InputStream input, ZipFile zipFile) {
            super(input);
            this.zipFile = zipFile;
        }

        @Override
        public void close() throws IOException {
            try (ZipFile archive = zipFile) {
                super.close();
            }
        }
    }
}
