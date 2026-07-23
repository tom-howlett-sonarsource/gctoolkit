// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared utilities for GC log source discovery, format detection,
 * and opening plain-text, ZIP, and GZIP log streams.
 */
public final class LogStreamSource {

    private static final Logger LOGGER = Logger.getLogger(LogStreamSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogStreamSource() {
    }

    /**
     * Detect the format of the given path by inspecting magic bytes
     * or checking whether it is a directory.
     *
     * @param path the path to inspect
     * @return the detected {@link FileFormat}
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        } else if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        } else if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        }
        return FileFormat.PLAINTEXT;
    }

    /**
     * Return the byte size of the given path. For regular files this is
     * the file size; for directories it is the sum of all immediate
     * children's sizes.
     *
     * @param path the path to measure
     * @return the size in bytes
     * @throws IOException if the path cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                return children.filter(Files::isRegularFile)
                        .mapToLong(LogStreamSource::fileSize)
                        .sum();
            }
        }
        return Files.size(path);
    }

    /**
     * Open a line stream from a single log file, dispatching on its format.
     * The caller is responsible for closing the returned stream.
     *
     * @param path   the path to the log file
     * @param format the detected format of the file
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> stream(Path path, FileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return streamZipFile(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a line stream from a ZIP archive that may contain multiple entries.
     * All non-directory entries are concatenated into a single stream.
     * The caller is responsible for closing the returned stream.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines covering all entries in the archive
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("resource")
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
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEach(streams::add);
        } catch (java.io.UncheckedIOException uioe) {
            throw uioe.getCause();
        }

        java.io.SequenceInputStream sequenceInputStream =
                new java.io.SequenceInputStream(streams.elements());
        return new BufferedReader(new InputStreamReader(sequenceInputStream)).lines();
    }

    @SuppressWarnings("java:S2095") // Resources are closed via the stream's onClose callback
    private static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    @SuppressWarnings("java:S2095") // Resources are closed via the stream's onClose callback
    private static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.warning(e.getMessage());
        }
    }

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            int byte1 = fis.read();
            int byte2 = fis.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return false;
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }
}
