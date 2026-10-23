// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for discovering the format of, sizing, and opening GC log sources.
 * A source may be a plain text file, a GZIP file, a ZIP file (in which case the
 * first non-directory entry is read) or a directory.
 */
public final class LogSources {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * The format of a log source.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY
    }

    private LogSources() {}

    /**
     * Discover the format of the source from its magic bytes. Files that cannot be
     * read, or are too short to carry a magic number, are treated as plain text.
     * @param path The path to the source.
     * @return The format of the source.
     */
    public static Format discover(Path path) {
        if (Files.isDirectory(path))
            return Format.DIRECTORY;
        try (InputStream input = Files.newInputStream(path)) {
            int magic1 = input.read();
            int magic2 = input.read();
            if (magic1 == GZIP_MAGIC1 && magic2 == GZIP_MAGIC2)
                return Format.GZIP;
            if (magic1 == ZIP_MAGIC1 && magic2 == ZIP_MAGIC2)
                return Format.ZIP;
        } catch (IOException ignored) {
            // unreadable sources fall through to plain text, failing later when opened
        }
        return Format.PLAINTEXT;
    }

    /**
     * The size in bytes of the source as stored on disk. For a directory, this is the
     * sum of the sizes of the regular files it directly contains.
     * @param path The path to the source.
     * @return The size in bytes.
     * @throws IOException if the source cannot be sized.
     */
    public static long size(Path path) throws IOException {
        if (!Files.isDirectory(path))
            return Files.size(path);
        try (Stream<Path> children = Files.list(path)) {
            long total = 0;
            for (Path child : (Iterable<Path>) children.filter(Files::isRegularFile)::iterator)
                total += Files.size(child);
            return total;
        }
    }

    /**
     * Open the source, decompressing it if required.
     * @param path The path to the source.
     * @return An uncompressed input stream; the caller is responsible for closing it.
     * @throws IOException if the source is a directory or cannot be opened.
     */
    public static InputStream open(Path path) throws IOException {
        switch (discover(path)) {
            case GZIP:
                return openGZip(path);
            case ZIP:
                return openZip(path);
            case PLAINTEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream the lines of the source, decompressing it if required. Closing the
     * returned stream closes the underlying source.
     * @param path The path to the source.
     * @return A stream of lines.
     * @throws IOException if the source is a directory or cannot be opened.
     */
    public static Stream<String> lines(Path path) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(open(path), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
    }

    private static InputStream openGZip(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return new BufferedInputStream(new GZIPInputStream(input));
        } catch (IOException | RuntimeException e) {
            input.close();
            throw e;
        }
    }

    private static InputStream openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return zipStream;
        } catch (IOException | RuntimeException e) {
            zipStream.close();
            throw e;
        }
    }
}
