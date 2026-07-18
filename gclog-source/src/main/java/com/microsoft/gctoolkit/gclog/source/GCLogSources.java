// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.source;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovers logical GC log sources and opens their content streams.
 */
public final class GCLogSources {

    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private GCLogSources() {
    }

    public enum Format {
        DIRECTORY,
        GZIP,
        PLAIN_TEXT,
        ZIP
    }

    public static Format format(Path path) throws IOException {
        Objects.requireNonNull(path);
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    public static List<GCLogSource> discover(Path path) throws IOException {
        switch (format(path)) {
            case DIRECTORY:
                try (Stream<Path> paths = Files.list(path)) {
                    return paths.filter(Files::isRegularFile)
                            .map(GCLogSources::plainSource)
                            .collect(Collectors.toList());
                }
            case GZIP:
                return Collections.singletonList(gzipSource(path));
            case ZIP:
                return zipSources(path);
            case PLAIN_TEXT:
            default:
                return Collections.singletonList(plainSource(path));
        }
    }

    public static GCLogSource first(Path path) throws IOException {
        List<GCLogSource> sources = discover(path);
        if (sources.isEmpty()) {
            throw new IOException("No GC log source found in " + path);
        }
        return sources.get(0);
    }

    public static GCLogSource find(Path path, String name) throws IOException {
        return discover(path).stream()
                .filter(source -> source.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IOException(name + " not found in " + path));
    }

    private static GCLogSource plainSource(Path path) {
        return new GCLogSource(path, path.getFileName().toString(), fileSize(path), () -> Files.newInputStream(path));
    }

    private static GCLogSource gzipSource(Path path) {
        return new GCLogSource(path, path.getFileName().toString(), -1,
                () -> new GZIPInputStream(Files.newInputStream(path)));
    }

    private static List<GCLogSource> zipSources(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> zipSource(path, entry))
                    .collect(Collectors.toList());
        }
    }

    private static GCLogSource zipSource(Path path, ZipEntry entry) {
        String name = entry.getName();
        return new GCLogSource(path, name, entry.getSize(), () -> openZipEntry(path, name));
    }

    private static InputStream openZipEntry(Path path, String name) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            throw new IOException(name + " not found in " + path);
        }
        final InputStream input;
        try {
            input = zipFile.getInputStream(entry);
        } catch (IOException exception) {
            zipFile.close();
            throw exception;
        }
        return new FilterInputStream(input) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zipFile.close();
                }
            }
        };
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return -1;
        }
    }
}
