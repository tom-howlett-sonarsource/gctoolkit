// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A plain, ZIP, or GZIP GC log source.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private final Path path;
    private final String zipEntryName;
    private final Format format;

    private GCLogSource(Path path, String zipEntryName, Format format) {
        this.path = Objects.requireNonNull(path);
        this.zipEntryName = zipEntryName;
        this.format = format;
    }

    /**
     * Creates a source for a path. For a ZIP file, opening the source selects its first file entry.
     * @param path source path
     * @return GC log source
     */
    public static GCLogSource from(Path path) {
        return new GCLogSource(path, null, detect(path));
    }

    /**
     * Creates a source for a named entry in a ZIP file.
     * @param path ZIP file path
     * @param entryName ZIP entry name
     * @return GC log source
     */
    public static GCLogSource fromZipEntry(Path path, String entryName) {
        return new GCLogSource(path, Objects.requireNonNull(entryName), Format.ZIP);
    }

    /**
     * Discovers sources contained by a path. Directories return their immediate children and ZIP
     * files return their non-directory entries. Other paths return a single source.
     * @param path path to inspect
     * @return discovered sources
     * @throws IOException if the path cannot be read
     */
    public static Stream<GCLogSource> discover(Path path) throws IOException {
        GCLogSource source = from(path);
        if (source.format == Format.DIRECTORY) {
            try (Stream<Path> children = Files.list(path)) {
                List<GCLogSource> sources = children
                        .map(GCLogSource::from)
                        .collect(Collectors.toList());
                return sources.stream();
            }
        }
        if (source.format == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                List<GCLogSource> sources = zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(ZipEntry::getName)
                        .map(name -> new GCLogSource(path, name, Format.ZIP))
                        .collect(Collectors.toList());
                return sources.stream();
            }
        }
        return Stream.of(source);
    }

    /**
     * Returns the source path.
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the ZIP entry name, or the path file name for non-ZIP sources.
     * @return source name
     */
    public String name() {
        return zipEntryName == null ? path.getFileName().toString() : zipEntryName;
    }

    /**
     * Returns the detected source format.
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the number of uncompressed bytes in this source.
     * @return uncompressed byte count
     * @throws IOException if the source cannot be read
     */
    public long byteSize() throws IOException {
        if (format == Format.DIRECTORY) {
            try (Stream<GCLogSource> sources = discover(path)) {
                long total = 0L;
                for (GCLogSource source : sources.collect(Collectors.toList())) {
                    total += source.byteSize();
                }
                return total;
            }
        }
        if (format == Format.PLAIN_TEXT) {
            return Files.size(path);
        }
        if (format == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                ZipEntry entry = findZipEntry(zipFile);
                long size = entry.getSize();
                if (size >= 0L) {
                    return size;
                }
            }
        }
        try (InputStream input = open()) {
            long size = 0L;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
            }
            return size;
        }
    }

    /**
     * Opens the uncompressed bytes for this source.
     * @return input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public InputStream open() throws IOException {
        if (format == Format.PLAIN_TEXT) {
            return Files.newInputStream(path);
        }
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            ZipFile zipFile = new ZipFile(path.toFile());
            try {
                ZipEntry entry = findZipEntry(zipFile);
                return new ZipFileInputStream(zipFile.getInputStream(entry), zipFile);
            } catch (IOException | RuntimeException exception) {
                zipFile.close();
                throw exception;
            }
        }
        throw new IOException("Unable to open directory as a GC log source: " + path);
    }

    /**
     * Opens this source as UTF-8 lines. Closing the stream closes its underlying source.
     * @return line stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(open()), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private ZipEntry findZipEntry(ZipFile zipFile) throws IOException {
        ZipEntry entry = zipEntryName == null
                ? zipFile.stream().filter(candidate -> !candidate.isDirectory()).findFirst().orElse(null)
                : zipFile.getEntry(zipEntryName);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("No file entry found in ZIP source: " + path);
        }
        return entry;
    }

    private static Format detect(Path path) {
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
        }
        return Format.PLAIN_TEXT;
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    private static final class ZipFileInputStream extends FilterInputStream {
        private final ZipFile zipFile;

        private ZipFileInputStream(InputStream input, ZipFile zipFile) {
            super(input);
            this.zipFile = zipFile;
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
