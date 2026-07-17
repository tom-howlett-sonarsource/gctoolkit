// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A filesystem source containing plain text, ZIP, or GZIP GC log data.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1F;
    private static final int GZIP_MAGIC_2 = 0x8B;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4B;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /**
     * Discovers the source format from its filesystem type and magic bytes.
     *
     * @param path source path
     * @return discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Format.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_1 && secondByte == GZIP_MAGIC_2) {
                return new GCLogSource(path, Format.GZIP);
            }
            if (firstByte == ZIP_MAGIC_1 && secondByte == ZIP_MAGIC_2) {
                return new GCLogSource(path, Format.ZIP);
            }
            return new GCLogSource(path, Format.PLAIN_TEXT);
        }
    }

    public Path path() {
        return path;
    }

    public Format format() {
        return format;
    }

    /**
     * Returns the source size in bytes as stored on disk.
     *
     * @return source byte size
     * @throws IOException if the size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }

    /**
     * Lists direct directory children or non-directory ZIP entry names.
     *
     * @return source entry names in discovery order
     * @throws IOException if entries cannot be discovered
     */
    public List<String> entries() throws IOException {
        if (format == Format.DIRECTORY) {
            try (Stream<Path> paths = Files.list(path)) {
                return paths.map(child -> child.getFileName().toString()).collect(Collectors.toList());
            }
        }
        if (format == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                return zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(ZipEntry::getName)
                        .collect(Collectors.toList());
            }
        }
        return List.of(path.getFileName().toString());
    }

    /**
     * Opens source lines, using the first non-directory ZIP entry when applicable.
     *
     * @return closeable line stream
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            case ZIP:
                List<String> entryNames = entries();
                if (entryNames.isEmpty()) {
                    throw new IOException("ZIP file contains no log entries: " + path);
                }
                return lines(entryNames.get(0));
            default:
                throw new IOException("Unable to open directory as a log stream: " + path);
        }
    }

    /**
     * Opens a named ZIP entry as lines.
     *
     * @param entryName ZIP entry name
     * @return closeable line stream
     * @throws IOException if the entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        if (format != Format.ZIP) {
            throw new IOException("Named entries are only supported for ZIP sources: " + path);
        }

        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return lines(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException exception) {
            zipFile.close();
            throw exception;
        }
    }

    /**
     * Opens all non-directory ZIP entries in archive order, or the single source otherwise.
     *
     * @return closeable line stream
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> allLines() throws IOException {
        if (format != Format.ZIP) {
            return lines();
        }
        return lines(new ZipEntriesInputStream(new ZipFile(path.toFile())));
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    private static final class ZipEntriesInputStream extends InputStream {

        private final ZipFile zipFile;
        private final Enumeration<? extends ZipEntry> entries;
        private InputStream currentEntry;

        private ZipEntriesInputStream(ZipFile zipFile) {
            this.zipFile = zipFile;
            this.entries = zipFile.entries();
        }

        @Override
        public int read() throws IOException {
            byte[] singleByte = new byte[1];
            int bytesRead = read(singleByte, 0, 1);
            return bytesRead == -1 ? -1 : Byte.toUnsignedInt(singleByte[0]);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            while (openNextEntry()) {
                int bytesRead = currentEntry.read(bytes, offset, length);
                if (bytesRead != -1) {
                    return bytesRead;
                }
                currentEntry.close();
                currentEntry = null;
            }
            return -1;
        }

        private boolean openNextEntry() throws IOException {
            while (currentEntry == null && entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    currentEntry = zipFile.getInputStream(entry);
                }
            }
            return currentEntry != null;
        }

        @Override
        public void close() throws IOException {
            List<IOException> failures = new ArrayList<>();
            if (currentEntry != null) {
                try {
                    currentEntry.close();
                } catch (IOException exception) {
                    failures.add(exception);
                }
            }
            try {
                zipFile.close();
            } catch (IOException exception) {
                failures.add(exception);
            }
            if (!failures.isEmpty()) {
                IOException failure = failures.remove(0);
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
        }
    }
}
