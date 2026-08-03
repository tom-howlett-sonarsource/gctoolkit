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
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A discovered GC log source. This class centralizes the file-system and
 * compression IO shared by toolkit modules.
 */
public final class GCLogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    /** Supported source formats. */
    public enum Format { ZIP, GZIP, PLAINTEXT, DIRECTORY }

    private final Path path;
    private final Format format;

    private GCLogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    /** Discover a source from its path and leading bytes. */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path))
            return new GCLogSource(path, Format.DIRECTORY);

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2)
                return new GCLogSource(path, Format.GZIP);
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2)
                return new GCLogSource(path, Format.ZIP);
            return new GCLogSource(path, Format.PLAINTEXT);
        }
    }

    public Path path() { return path; }

    public Format format() { return format; }

    public boolean isDirectory() { return format == Format.DIRECTORY; }

    /**
     * Return the number of readable bytes in this source. For compressed
     * sources this is the uncompressed size of the first file entry.
     */
    public long byteSize() throws IOException {
        if (format == Format.PLAINTEXT)
            return Files.size(path);
        if (format == Format.DIRECTORY)
            throw new IOException("Unable to size directory " + path);
        try (InputStream input = openStream()) {
            long size = 0;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1)
                size += count;
            return size;
        }
    }

    /** Open the plain, GZIP, or first non-directory ZIP entry. */
    public InputStream openStream() throws IOException {
        if (format == Format.DIRECTORY)
            throw new IOException("Unable to open directory " + path);
        InputStream input = Files.newInputStream(path);
        if (format == Format.PLAINTEXT)
            return input;
        if (format == Format.GZIP)
            return new GZIPInputStream(input);

        ZipInputStream zip = new ZipInputStream(input);
        ZipEntry entry;
        do {
            entry = zip.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return zip;
    }

    /** Open this source as a lazily read stream of lines. */
    public Stream<String> lines() throws IOException {
        if (format == Format.PLAINTEXT)
            return Files.lines(path);
        return new BufferedReader(new InputStreamReader(
                new BufferedInputStream(openStream()))).lines();
    }
}
