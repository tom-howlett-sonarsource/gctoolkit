// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A discovered plain, ZIP, or GZIP log source. */
public final class LogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    public enum Format { PLAIN, ZIP, GZIP, DIRECTORY }

    private final Path path;
    private final Format format;

    private LogSource(Path path, Format format) {
        this.path = path;
        this.format = format;
    }

    public static LogSource discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new LogSource(path, Format.DIRECTORY);
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return new LogSource(path, Format.GZIP);
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return new LogSource(path, Format.ZIP);
            }
            return new LogSource(path, Format.PLAIN);
        }
    }

    public Path path() { return path; }

    public Format format() { return format; }

    /** Returns the source's on-disk byte count. */
    public long size() throws IOException { return Files.size(path); }

    public InputStream open() throws IOException {
        switch (format) {
            case PLAIN:
                return Files.newInputStream(path);
            case GZIP:
                return new GZIPInputStream(Files.newInputStream(path));
            case ZIP:
                return openFirstZipEntry();
            default:
                throw new IOException("Unable to open directory as a log stream: " + path);
        }
    }

    public Stream<String> lines() throws IOException {
        Charset charset = format == Format.PLAIN ? StandardCharsets.UTF_8 : Charset.defaultCharset();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(open()), charset));
        return reader.lines().onClose(() -> close(reader));
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                return input;
            }
        }
        input.close();
        throw new IOException("ZIP source contains no file entries: " + path);
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report an IOException.
        }
    }
}
