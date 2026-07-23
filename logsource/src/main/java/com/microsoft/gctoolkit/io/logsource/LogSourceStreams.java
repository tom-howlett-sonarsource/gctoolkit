// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
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
 * Shared utilities for opening GC log streams from plain-text, ZIP, and GZIP sources,
 * and for detecting file formats via magic bytes.
 */
public final class LogSourceStreams {

    private LogSourceStreams() {
        // utility class
    }

    // Magic byte constants for format detection
    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * The supported file formats for GC log sources.
     */
    public enum FileFormat {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN
    }

    /**
     * Detect the file format of the given path by examining magic bytes
     * and filesystem attributes.
     *
     * @param path the path to examine
     * @return the detected file format
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        } else if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        } else if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        } else {
            return FileFormat.PLAINTEXT;
        }
    }

    /**
     * Check whether the first two bytes of the file at the given path
     * match the specified magic bytes.
     *
     * @param path   the file to inspect
     * @param magic1 expected first byte
     * @param magic2 expected second byte
     * @return {@code true} if the bytes match
     */
    public static boolean matchesMagic(Path path, int magic1, int magic2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int byte1 = in.read();
            int byte2 = in.read();
            return byte1 == magic1 && byte2 == magic2;
        } catch (IOException ioe) {
            return false;
        }
    }

    /**
     * Stream lines from a plain-text file.
     *
     * @param path the path to the file
     * @return a stream of lines
     * @throws IOException if an I/O error occurs
     */
    public static Stream<String> streamPlainTextFile(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream lines from the first non-directory entry in a ZIP file.
     * This is suitable for single-entry ZIP archives containing a GC log.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from the first file entry
     * @throws IOException if an I/O error occurs or the ZIP has no file entries
     */
    public static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Stream lines from a GZIP-compressed file.
     *
     * @param path the path to the GZIP file
     * @return a stream of lines
     * @throws IOException if an I/O error occurs
     */
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Stream lines from a multi-entry ZIP file by concatenating all non-directory
     * entries into a single stream. Suitable for rotating log archives.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from all file entries
     * @throws IOException if an I/O error occurs
     */
    public static Stream<String> streamMultiEntryZipFile(Path path) throws IOException {
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
            throw uioe.getCause();
        }

        SequenceInputStream sequenceInputStream = new SequenceInputStream(streams.elements());
        return new BufferedReader(new InputStreamReader(sequenceInputStream)).lines();
    }

    /**
     * Open a line stream for the given path, dispatching on file format.
     *
     * @param path   the file to stream
     * @param format the detected format of the file
     * @return a stream of lines
     * @throws IOException if an I/O error occurs or the format is unsupported
     */
    public static Stream<String> streamByFormat(Path path, FileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return streamPlainTextFile(path);
            case ZIP:
                return streamZipFile(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unable to stream file: " + path);
        }
    }
}
