// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Shared utilities for opening GC log sources as line streams, discovering
 * entries inside archives, and querying file size.  These methods consolidate
 * the duplicated IO behaviour that was previously scattered across the API and
 * parser modules.
 *
 * <p>Streams returned by the {@code *Lines} methods register an
 * {@link Stream#onClose} handler that closes the underlying IO resources.
 * Callers should use try-with-resources on the returned stream.</p>
 */
public final class LogSourceStreams {

    private LogSourceStreams() { }

    // ---- stream opening ----------------------------------------------------

    /**
     * Stream the lines of a plain-text file.
     *
     * @param path path to the file
     * @return a {@code Stream} of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the lines of the first non-directory entry inside a ZIP file.
     *
     * @param path path to the ZIP file
     * @return a {@code Stream} of lines from the first data entry
     * @throws IOException if the file cannot be read or contains no data entries
     */
    @SuppressWarnings("resource") // reader is closed via Stream.onClose()
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
            return reader.lines().onClose(() -> closeQuietly(reader));
        } catch (IOException e) {
            zipStream.close();
            throw e;
        }
    }

    /**
     * Stream the lines of a GZIP-compressed file.
     *
     * @param path path to the GZIP file
     * @return a {@code Stream} of lines
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("resource") // reader is closed via Stream.onClose()
    public static Stream<String> gzipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Stream the lines of every non-directory entry inside a ZIP file,
     * concatenated in archive order.
     *
     * @param path path to the ZIP file
     * @return a {@code Stream} of lines across all entries
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("resource")
    public static Stream<String> allZipEntryLines(Path path) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        List<ZipEntry> entries = zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .collect(Collectors.toList());
        Vector<InputStream> streams = new Vector<>();

        try {
            entries.stream()
                    .map(entry -> {
                        try {
                            return zipFile.getInputStream(entry);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEach(streams::add);
        } catch (UncheckedIOException uioe) {
            zipFile.close();
            throw uioe.getCause();
        }

        SequenceInputStream sequenceInputStream = new SequenceInputStream(streams.elements());
        BufferedReader reader = new BufferedReader(new InputStreamReader(sequenceInputStream));
        return reader.lines().onClose(() -> {
            closeQuietly(reader);
            closeQuietly(zipFile);
        });
    }

    /**
     * Stream the lines of a single named entry inside a ZIP file.
     *
     * @param zipPath   path to the ZIP file
     * @param entryName the name of the entry to read
     * @return a {@code Stream} of lines from the entry
     * @throws IOException if the file cannot be read or the entry does not exist
     */
    @SuppressWarnings("resource")
    public static Stream<String> zipEntryLines(Path zipPath, String entryName) throws IOException {
        ZipFile file = new ZipFile(zipPath.toFile());
        ZipEntry entry = file.getEntry(entryName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(entry)));
        return reader.lines().onClose(() -> {
            closeQuietly(reader);
            closeQuietly(file);
        });
    }

    // ---- source discovery --------------------------------------------------

    /**
     * List the names of all non-directory entries inside a ZIP file.
     *
     * @param path path to the ZIP file
     * @return an unmodifiable list of entry names
     * @throws IOException if the file cannot be read
     */
    public static List<String> listZipEntries(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    // ---- byte sizing -------------------------------------------------------

    /**
     * Return the size of the file in bytes.
     *
     * @param path path to the file
     * @return file size in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    // ---- internal helpers --------------------------------------------------

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) { // AutoCloseable.close() throws Exception
        }
    }
}
