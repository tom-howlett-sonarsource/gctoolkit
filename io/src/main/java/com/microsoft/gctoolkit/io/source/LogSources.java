// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Discovery utilities for plain, ZIP, and GZIP GC log sources.
 */
public final class LogSources {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private LogSources() {
    }

    /**
     * Discovers readable log sources at a path. Directories yield their regular files and ZIP
     * archives yield their non-directory entries.
     *
     * @param path file, archive, or directory to inspect
     * @return discovered sources
     * @throws IOException if the path cannot be inspected
     */
    public static List<LogSource> discover(Path path) throws IOException {
        Objects.requireNonNull(path);
        LogSourceFormat format = format(path);
        switch (format) {
            case DIRECTORY:
                try (Stream<Path> paths = Files.list(path)) {
                    return paths.filter(Files::isRegularFile)
                            .map(LogSources::plainSource)
                            .collect(Collectors.toList());
                }
            case ZIP:
                try (ZipFile zipFile = new ZipFile(path.toFile())) {
                    return zipFile.stream()
                            .filter(entry -> !entry.isDirectory())
                            .map(entry -> new LogSource(path, entry.getName(), format, entry.getSize()))
                            .collect(Collectors.toList());
                }
            default:
                return List.of(new LogSource(path, null, format, -1));
        }
    }

    /**
     * Returns the first readable source at a path.
     *
     * @param path file or archive to inspect
     * @return first readable source
     * @throws IOException if no source is present or the path cannot be inspected
     */
    public static LogSource first(Path path) throws IOException {
        List<LogSource> sources = discover(path);
        if (sources.isEmpty()) {
            throw new IOException("No log sources found at " + path);
        }
        return sources.get(0);
    }

    /**
     * Finds a named source inside a ZIP archive.
     *
     * @param path ZIP archive path
     * @param name entry name
     * @return matching source
     * @throws IOException if the entry cannot be found
     */
    public static LogSource find(Path path, String name) throws IOException {
        Objects.requireNonNull(name);
        return discover(path).stream()
                .filter(source -> name.equals(source.getName()))
                .findFirst()
                .orElseThrow(() -> new IOException("Log source not found: " + name));
    }

    /**
     * Detects a source format from its path and magic bytes.
     *
     * @param path source path
     * @return detected format
     * @throws IOException if the path cannot be inspected
     */
    public static LogSourceFormat format(Path path) throws IOException {
        Objects.requireNonNull(path);
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1 && secondByte == GZIP_MAGIC_BYTE_2) {
                return LogSourceFormat.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1 && secondByte == ZIP_MAGIC_BYTE_2) {
                return LogSourceFormat.ZIP;
            }
            return LogSourceFormat.PLAIN_TEXT;
        }
    }

    private static LogSource plainSource(Path path) {
        return new LogSource(path, null, LogSourceFormat.PLAIN_TEXT, -1);
    }
}
