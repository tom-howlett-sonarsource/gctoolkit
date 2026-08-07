// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A file-system source containing a plain, ZIP, or GZIP GC log.
 */
public final class LogSource {

    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private final Path path;

    public LogSource(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public Path getPath() {
        return path;
    }

    /**
     * Discovers regular log sources directly below a directory, or returns the supplied file.
     */
    public static List<LogSource> discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isDirectory(path)) {
            return List.of(new LogSource(path));
        }
        try (Stream<Path> children = Files.list(path)) {
            return children.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(LogSource::new)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Returns the on-disk byte size of this source. Directory sizes are the sum of discovered files.
     */
    public long byteSize() throws IOException {
        if (!Files.isDirectory(path)) {
            return Files.size(path);
        }
        long size = 0L;
        for (LogSource source : discover(path)) {
            size = Math.addExact(size, source.byteSize());
        }
        return size;
    }

    public Format format() throws IOException {
        return format(path);
    }

    public static Format format(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
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
            return Format.PLAINTEXT;
        }
    }

    /**
     * Opens the source contents. For ZIP files, the first non-directory entry is selected.
     */
    public InputStream openStream() throws IOException {
        Format sourceFormat = format();
        if (sourceFormat == Format.DIRECTORY) {
            throw new IOException("Unable to open directory as a log stream: " + path);
        }

        InputStream fileStream = Files.newInputStream(path);
        try {
            if (sourceFormat == Format.GZIP) {
                return new GZIPInputStream(fileStream);
            }
            if (sourceFormat == Format.ZIP) {
                ZipInputStream zipStream = new ZipInputStream(fileStream);
                ZipEntry entry;
                while ((entry = zipStream.getNextEntry()) != null && entry.isDirectory()) {
                    // Find the first file entry.
                }
                if (entry == null) {
                    zipStream.close();
                    throw new IOException("ZIP source contains no file entries: " + path);
                }
                return zipStream;
            }
            return fileStream;
        } catch (IOException | RuntimeException exception) {
            fileStream.close();
            throw exception;
        }
    }

    /**
     * Opens the source as a lazily read stream of lines.
     */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(openStream())));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked IO failures.
        }
    }

    public enum Format {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY
    }
}
