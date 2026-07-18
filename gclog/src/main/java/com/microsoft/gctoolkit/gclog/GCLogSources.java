// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Factory and discovery utilities for {@link GCLogSource} instances.
 */
public final class GCLogSources {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSources() {
    }

    /**
     * Discovers readable sources in a path. Directories yield their regular files,
     * ZIP files yield their non-directory entries, and other files yield one source.
     *
     * @param path path to inspect
     * @return discovered sources
     * @throws IOException if the path cannot be inspected
     */
    public static List<GCLogSource> discover(Path path) throws IOException {
        Objects.requireNonNull(path);
        Format format = format(path);
        if (format == Format.DIRECTORY) {
            try (Stream<Path> paths = Files.list(path)) {
                List<Path> regularFiles = paths.filter(Files::isRegularFile).collect(Collectors.toList());
                List<GCLogSource> sources = new ArrayList<>(regularFiles.size());
                for (Path regularFile : regularFiles) {
                    sources.add(first(regularFile));
                }
                return sources;
            }
        }
        if (format == Format.ZIP) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                return zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(ZipEntry::getName)
                        .map(name -> new GCLogSource(path, name, Format.ZIP))
                        .collect(Collectors.toList());
            }
        }
        return List.of(new GCLogSource(path, null, format));
    }

    /**
     * Returns the first readable source discovered in a path.
     *
     * @param path path to inspect
     * @return first source
     * @throws IOException if the path has no readable source or cannot be inspected
     */
    public static GCLogSource first(Path path) throws IOException {
        return discover(path).stream()
                .findFirst()
                .orElseThrow(() -> new IOException("No GC log source found in " + path));
    }

    /**
     * Returns a source for a specific ZIP entry.
     *
     * @param path path to the ZIP file
     * @param entryName ZIP entry name
     * @return ZIP entry source
     * @throws IOException if the path is not a ZIP file
     */
    public static GCLogSource zipEntry(Path path, String entryName) throws IOException {
        Objects.requireNonNull(entryName);
        if (format(path) != Format.ZIP) {
            throw new IOException(path + " is not a ZIP file");
        }
        return new GCLogSource(path, entryName, Format.ZIP);
    }

    /**
     * Detects the source format using the path type and compression magic bytes.
     *
     * @param path path to inspect
     * @return detected format
     * @throws IOException if the path cannot be inspected
     */
    public static Format format(Path path) throws IOException {
        Objects.requireNonNull(path);
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

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
