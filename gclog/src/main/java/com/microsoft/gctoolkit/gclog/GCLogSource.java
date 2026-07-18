// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.util.stream.Collectors.toList;

/**
 * File-system and stream operations shared by GC log consumers.
 */
public final class GCLogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_FIRST = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private GCLogSource() {
    }

    /**
     * Finds the immediate children of a directory.
     *
     * @param directory directory to inspect
     * @return discovered paths in file-system order
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> discover(final Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(toList());
        }
    }

    /**
     * Returns the source size in bytes.
     *
     * @param source source to measure
     * @return source size in bytes
     * @throws IOException if the source cannot be read
     */
    public static long size(final Path source) throws IOException {
        return Files.size(source);
    }

    /**
     * Opens lines from a plain, ZIP, or GZIP source, identified by magic bytes.
     * For ZIP sources, the first non-directory entry is used.
     *
     * @param source source to open
     * @return lazily read lines; callers must close the stream
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(final Path source) throws IOException {
        if (Files.isDirectory(source)) {
            throw new IOException("Unable to read " + source);
        }

        int[] magic = readMagic(source);
        if (isMagic(magic, ZIP_MAGIC_FIRST, ZIP_MAGIC_SECOND)) {
            return zipLines(source);
        }
        if (isMagic(magic, GZIP_MAGIC_FIRST, GZIP_MAGIC_SECOND)) {
            return compressedLines(
                    new GZIPInputStream(Files.newInputStream(source)));
        }
        return Files.lines(source);
    }

    private static int[] readMagic(final Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            return new int[]{input.read(), input.read()};
        }
    }

    private static boolean isMagic(final int[] magic, final int first,
                                   final int second) {
        return magic[0] == first && magic[1] == second;
    }

    private static Stream<String> zipLines(final Path source)
            throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return compressedLines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static Stream<String> compressedLines(final InputStream input) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
