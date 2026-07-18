// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovers GC log sources and opens plain, ZIP, and GZIP content.
 */
public final class LogSources {

    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    enum Format {
        PLAIN,
        ZIP,
        GZIP
    }

    private LogSources() {
    }

    /**
     * Discovers readable sources at a path. Directories yield their regular
     * files, ZIP files yield non-directory entries, and other files yield one source.
     * @param path file or directory to inspect
     * @return discovered sources
     * @throws IOException if the path cannot be inspected
     */
    public static List<LogSource> discover(Path path) throws IOException {
        Objects.requireNonNull(path);
        if (Files.isDirectory(path)) {
            try (var paths = Files.list(path)) {
                List<LogSource> sources = new ArrayList<>();
                var iterator = paths.filter(Files::isRegularFile).iterator();
                while (iterator.hasNext()) {
                    sources.add(fileSource(iterator.next(), Format.PLAIN));
                }
                return List.copyOf(sources);
            }
        }

        Format format = detect(path);
        if (format == Format.ZIP) {
            return discoverZip(path);
        }
        return List.of(fileSource(path, format));
    }

    /**
     * Returns the first readable source at a path. For ZIP files this is the
     * first non-directory entry.
     * @param path file or directory to inspect
     * @return first discovered source
     * @throws IOException if no readable source exists
     */
    public static LogSource first(Path path) throws IOException {
        return discover(path).stream()
                .findFirst()
                .orElseThrow(() -> new IOException("No log source found in " + path));
    }

    /**
     * Returns a specific ZIP entry as a source.
     * @param path ZIP file path
     * @param entryName entry name
     * @return matching source
     * @throws IOException if the entry does not exist or is a directory
     */
    public static LogSource zipEntry(Path path, String entryName) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(entryName);
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return zipSource(path, entry);
        }
    }

    static InputStream open(LogSource source) throws IOException {
        switch (source.format()) {
            case PLAIN:
                return Files.newInputStream(source.getPath());
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(source.getPath()));
            case ZIP:
                return openZip(source.getPath(), source.zipEntryName());
            default:
                throw new IOException("Unsupported log source: " + source.getPath());
        }
    }

    private static List<LogSource> discoverZip(Path path) throws IOException {
        List<LogSource> sources = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> zipSource(path, entry))
                    .forEach(sources::add);
        }
        return List.copyOf(sources);
    }

    private static LogSource fileSource(Path path, Format format) throws IOException {
        return new LogSource(path, path.getFileName().toString(), Files.size(path), format, null);
    }

    private static LogSource zipSource(Path path, ZipEntry entry) {
        return new LogSource(path, entry.getName(), entry.getSize(), Format.ZIP, entry.getName());
    }

    private static Format detect(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
            return Format.PLAIN;
        }
    }

    private static InputStream openZip(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry not found: " + entryName);
            }
            return new ZipFileInputStream(zipFile, zipFile.getInputStream(entry));
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
    }

    private static final class ZipFileInputStream extends FilterInputStream {

        private final ZipFile zipFile;

        private ZipFileInputStream(ZipFile zipFile, InputStream input) {
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
