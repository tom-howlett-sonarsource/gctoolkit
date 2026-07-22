// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared utility for GC log source format detection, stream opening,
 * byte sizing, and source discovery. Provides the low-level IO operations
 * used by both the API and parser modules.
 */
public final class GcLogSource {

    private static final Logger LOGGER = Logger.getLogger(GcLogSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private GcLogSource() {
    }

    /**
     * Detect the format of the file at the given path by inspecting
     * magic bytes or checking if the path is a directory.
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

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            return in.read() == expected1 && in.read() == expected2;
        } catch (IOException e) {
            LOGGER.warning(e.getMessage());
            return false;
        }
    }

    /**
     * Return the size of the file in bytes.
     *
     * @param path the file path
     * @return size in bytes
     * @throws IOException if the file cannot be read
     */
    public static long byteCount(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Stream lines from a plain-text file.
     *
     * @param path the file path
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream lines from the first non-directory entry in a ZIP file.
     *
     * @param path the ZIP file path
     * @return a stream of lines from the first file entry
     * @throws IOException if the ZIP cannot be read
     */
    @SuppressWarnings("resource") // resources are closed via Stream.onClose()
    public static Stream<String> streamZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            }
        });
    }

    /**
     * Stream lines from a named entry within a ZIP file.
     *
     * @param zipPath   the ZIP file path
     * @param entryName the name of the entry to stream
     * @return a stream of lines from the named entry
     * @throws IOException if the ZIP or entry cannot be read
     */
    @SuppressWarnings("resource") // resources are closed via Stream.onClose()
    public static Stream<String> streamZipEntry(Path zipPath, String entryName) throws IOException {
        ZipFile file = new ZipFile(zipPath.toFile());
        ZipEntry entry = file.getEntry(entryName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(entry)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
                file.close();
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            }
        });
    }

    /**
     * Stream lines from all non-directory entries in a ZIP file,
     * concatenated in archive order via {@link SequenceInputStream}.
     *
     * @param path the ZIP file path
     * @return a stream of lines from all file entries
     * @throws IOException if the ZIP cannot be read
     */
    @SuppressWarnings("resource") // resources are closed via Stream.onClose()
    public static Stream<String> streamMultiEntryZip(Path path) throws IOException {
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
        BufferedReader reader = new BufferedReader(new InputStreamReader(sequenceInputStream));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
                zipFile.close();
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            }
        });
    }

    /**
     * Stream lines from a GZIP-compressed file.
     *
     * @param path the GZIP file path
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("resource") // resources are closed via Stream.onClose()
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            }
        });
    }

    /**
     * Auto-detect the format of the file at the given path and stream its lines.
     * Supports plain text, ZIP (first entry), and GZIP formats.
     *
     * @param path the file path
     * @return a stream of lines
     * @throws IOException if the format is unsupported or the file cannot be read
     */
    public static Stream<String> stream(Path path) throws IOException {
        FileFormat format = detectFormat(path);
        if (format == FileFormat.PLAINTEXT) {
            return streamPlainText(path);
        } else if (format == FileFormat.ZIP) {
            return streamZipEntry(path);
        } else if (format == FileFormat.GZIP) {
            return streamGZip(path);
        }
        throw new IOException("Unable to stream path: " + path);
    }

    /**
     * List all files in the given directory.
     *
     * @param directory the directory to list
     * @return a stream of paths in the directory
     * @throws IOException if the directory cannot be listed
     */
    public static Stream<Path> discoverSources(Path directory) throws IOException {
        return Files.list(directory);
    }

    /**
     * List files in the given directory whose names start with the given prefix.
     *
     * @param directory the directory to list
     * @param prefix    the filename prefix to filter by
     * @return a stream of matching paths
     * @throws IOException if the directory cannot be listed
     */
    public static Stream<Path> discoverSources(Path directory, String prefix) throws IOException {
        return Files.list(directory)
                .filter(file -> file.getFileName().toString().startsWith(prefix));
    }

    /**
     * List the names of all non-directory entries in the given ZIP file.
     *
     * @param zipPath the ZIP file path
     * @return a stream of entry names
     * @throws IOException if the ZIP cannot be read
     */
    public static Stream<String> discoverZipEntries(Path zipPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            List<String> entries = zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
            return entries.stream();
        }
    }
}
