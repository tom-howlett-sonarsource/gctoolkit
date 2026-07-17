// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared filesystem and stream operations for GC log sources.
 */
public final class GCLogSource {

    /** First GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    /** Second GZIP magic byte. */
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    /** First ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    /** Second ZIP magic byte. */
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private GCLogSource() {
    }

    /**
     * Detect the source format from the filesystem type and leading bytes.
     *
     * @param source source path
     * @return detected source format
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSourceFormat format(final Path source)
            throws IOException {
        Objects.requireNonNull(source, "source");
        if (Files.isDirectory(source)) {
            return GCLogSourceFormat.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(source)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1
                    && secondByte == GZIP_MAGIC_BYTE_2) {
                return GCLogSourceFormat.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1
                    && secondByte == ZIP_MAGIC_BYTE_2) {
                return GCLogSourceFormat.ZIP;
            }
            return GCLogSourceFormat.PLAIN_TEXT;
        }
    }

    /**
     * Discover sources in a directory or alongside a supplied file.
     *
     * @param source directory or representative file
     * @param matcher source filter
     * @return matching paths in deterministic order
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> discover(final Path source,
                                      final Predicate<Path> matcher)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(matcher, "matcher");
        Path directory = Files.isDirectory(source)
                ? source
                : source.getParent();
        if (directory == null) {
            directory = Path.of(".");
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(matcher).sorted().collect(Collectors.toList());
        }
    }

    /**
     * Return the physical size of a source file.
     *
     * @param source source path
     * @return source size in bytes
     * @throws IOException if the source is not a regular file or cannot be read
     */
    public static long size(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (!Files.isRegularFile(source)) {
            throw new IOException(
                    "GC log source is not a regular file: " + source);
        }
        return Files.size(source);
    }

    /**
     * Return non-directory entries from a ZIP source.
     *
     * @param source ZIP source path
     * @return entry names in archive order
     * @throws IOException if the archive cannot be read
     */
    public static List<String> entries(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        try (ZipFile zipFile = new ZipFile(source.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open a plain, ZIP, or GZIP source as a stream of lines. For ZIP sources,
     * the first non-directory entry is opened.
     *
     * @param source source path
     * @return closeable stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> open(final Path source) throws IOException {
        GCLogSourceFormat sourceFormat = format(source);
        switch (sourceFormat) {
            case PLAIN_TEXT:
                return openPlain(source);
            case ZIP:
                return openFirstZipEntry(source);
            case GZIP:
                return lines(
                        new GZIPInputStream(Files.newInputStream(source)),
                        Charset.defaultCharset());
            default:
                throw new IOException("Unable to read GC log source " + source);
        }
    }

    /**
     * Open a plain-text source as a stream of lines.
     *
     * @param source source path
     * @return closeable stream of lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> openPlain(final Path source)
            throws IOException {
        Objects.requireNonNull(source, "source");
        return lines(Files.newInputStream(source), StandardCharsets.UTF_8);
    }

    /**
     * Open a named entry from a ZIP source as a stream of lines.
     *
     * @param source ZIP source path
     * @param entryName archive entry name
     * @return closeable stream of lines
     * @throws IOException if the source or entry cannot be opened
     */
    public static Stream<String> open(final Path source,
                                      final String entryName)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(entryName, "entryName");
        ZipInputStream zipInput = new ZipInputStream(
                Files.newInputStream(source));
        try {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    return lines(zipInput, Charset.defaultCharset());
                }
            }
        } catch (IOException exception) {
            zipInput.close();
            throw exception;
        }
        zipInput.close();
        throw new IOException("ZIP entry not found: " + entryName);
    }

    private static Stream<String> openFirstZipEntry(final Path source)
            throws IOException {
        ZipInputStream zipInput = new ZipInputStream(
                Files.newInputStream(source));
        try {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    return lines(zipInput, Charset.defaultCharset());
                }
            }
        } catch (IOException exception) {
            zipInput.close();
            throw exception;
        }
        return lines(zipInput, Charset.defaultCharset());
    }

    private static Stream<String> lines(final InputStream input,
                                        final Charset charset) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, charset));
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
