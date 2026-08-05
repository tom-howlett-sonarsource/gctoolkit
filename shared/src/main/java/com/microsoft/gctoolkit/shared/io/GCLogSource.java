// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * File-system and stream operations shared by GC log data sources.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1F;
    private static final int GZIP_MAGIC_2 = 0x8B;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4B;

    private GCLogSource() {
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }

    /**
     * Discover the source format from the path and, for files, its magic bytes.
     * An unreadable non-directory source remains classified as plain text so the
     * eventual open operation reports the underlying IO error.
     *
     * @param source source path
     * @return discovered source format
     */
    public static Format discover(Path source) {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return Format.DIRECTORY;
        }
        if (hasMagic(source, GZIP_MAGIC_1, GZIP_MAGIC_2)) {
            return Format.GZIP;
        }
        if (hasMagic(source, ZIP_MAGIC_1, ZIP_MAGIC_2)) {
            return Format.ZIP;
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Test the first two bytes of a source.
     *
     * @param source source path
     * @param first expected first byte
     * @param second expected second byte
     * @return {@code true} when both bytes match
     */
    public static boolean hasMagic(Path source, int first, int second) {
        Objects.requireNonNull(source, "source");
        try (InputStream input = Files.newInputStream(source)) {
            return input.read() == first && input.read() == second;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Return the stored byte size of a source.
     *
     * @param source source path
     * @return byte size
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path source) throws IOException {
        return Files.size(Objects.requireNonNull(source, "source"));
    }

    /**
     * Discover the immediate paths contained in a directory.
     *
     * @param directory directory to inspect
     * @return discovered paths
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> discoverSources(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        try (Stream<Path> sources = Files.list(directory)) {
            return sources.collect(Collectors.toList());
        }
    }

    /**
     * Open a plain, ZIP, or GZIP source as a stream of lines. ZIP sources use
     * the first non-directory entry.
     *
     * @param source source path
     * @return lines from the source
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(Path source) throws IOException {
        Format format = discover(source);
        if (format == Format.PLAIN_TEXT) {
            return Files.lines(source);
        }
        if (format == Format.ZIP) {
            return openFirstZipEntry(source);
        }
        if (format == Format.GZIP) {
            return lines(new GZIPInputStream(Files.newInputStream(source)));
        }
        throw new IOException("Unable to read " + source);
    }

    /**
     * List the non-directory entries in a ZIP source.
     *
     * @param source ZIP source path
     * @return entry names in archive order
     * @throws IOException if the archive cannot be read
     */
    public static List<String> zipEntries(Path source) throws IOException {
        try (ZipFile zipFile = new ZipFile(source.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open one named entry in a ZIP source.
     *
     * @param source ZIP source path
     * @param entryName entry to open
     * @return lines from the entry
     * @throws IOException if the archive or entry cannot be opened
     */
    public static Stream<String> openZipEntry(Path source, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(source.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to find ZIP entry " + entryName + " in " + source);
            }
            return lines(zipFile.getInputStream(entry), zipFile);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipFile, exception);
            throw exception;
        }
    }

    /**
     * Open all non-directory entries in a ZIP source in archive order.
     *
     * @param source ZIP source path
     * @return lines from all entries
     * @throws IOException if the archive cannot be opened
     */
    public static Stream<String> openZipEntries(Path source) throws IOException {
        ZipFile zipFile = new ZipFile(source.toFile());
        Vector<InputStream> inputs = new Vector<>();
        try {
            for (String entryName : zipEntries(zipFile)) {
                inputs.add(zipFile.getInputStream(zipFile.getEntry(entryName)));
            }
            return lines(new SequenceInputStream(inputs.elements()), zipFile);
        } catch (IOException | RuntimeException exception) {
            for (InputStream input : inputs) {
                closeAfterFailure(input, exception);
            }
            closeAfterFailure(zipFile, exception);
            throw exception;
        }
    }

    private static Stream<String> openFirstZipEntry(Path source) throws IOException {
        ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(source));
        try {
            ZipEntry entry;
            do {
                entry = zipInput.getNextEntry();
            } while (entry != null && entry.isDirectory());
            if (entry == null) {
                zipInput.close();
                return Stream.empty();
            }
            return lines(zipInput);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipInput, exception);
            throw exception;
        }
    }

    private static List<String> zipEntries(ZipFile zipFile) {
        return Collections.list(zipFile.entries()).stream()
                .filter(entry -> !entry.isDirectory())
                .map(ZipEntry::getName)
                .collect(Collectors.toList());
    }

    private static Stream<String> lines(InputStream input, Closeable... additionalResources) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
                for (Closeable resource : additionalResources) {
                    resource.close();
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private static void closeAfterFailure(Closeable resource, Exception original) {
        try {
            resource.close();
        } catch (IOException closeException) {
            original.addSuppressed(closeException);
        }
    }
}
