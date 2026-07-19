// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared utilities for GC log source discovery, format detection,
 * and stream opening used by both the API and parser modules.
 */
public final class GcLogSource {

    private static final Logger LOG = Logger.getLogger(GcLogSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private GcLogSource() {
    }

    /**
     * Detect the format of the file at the given path by inspecting
     * magic bytes or checking whether it is a directory.
     */
    public static LogSourceFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return LogSourceFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogSourceFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogSourceFormat.ZIP;
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * Return the size of the log source in bytes.
     * For directories, returns the sum of all regular file sizes within.
     */
    public static long sizeInBytes(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                return children
                        .filter(Files::isRegularFile)
                        .mapToLong(GcLogSource::fileSize)
                        .sum();
            }
        }
        return Files.size(path);
    }

    /**
     * Open a line-based stream for the file at the given path,
     * automatically detecting the format.
     */
    public static Stream<String> stream(Path path) throws IOException {
        return stream(path, detectFormat(path));
    }

    /**
     * Open a line-based stream for the file at the given path
     * using the specified format.
     */
    public static Stream<String> stream(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return streamPlainText(path);
            case ZIP:
                return streamZipFile(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Cannot stream format " + format + " for " + path);
        }
    }

    /**
     * List the entry names contained in a ZIP file, excluding directories.
     */
    public static List<String> listZipEntries(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open a line-based stream for a single named entry within a ZIP file.
     */
    @SuppressWarnings("java:S2095") // resources are closed via Stream.onClose()
    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            zipFile.close();
            throw new IOException("ZIP entry not found: " + entryName + " in " + path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
                zipFile.close();
            } catch (IOException e) {
                LOG.warning(e.getMessage());
            }
        });
    }

    /**
     * Discover log file paths that share a common rotating-log root with
     * the given path. If the path is a directory, all files in it are returned.
     */
    public static List<Path> discoverRotatingLogFiles(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                return children
                        .filter(Files::isRegularFile)
                        .collect(Collectors.toList());
            }
        }
        String rootPattern = extractRootPattern(path);
        Path parent = path.getParent();
        if (parent == null) {
            parent = path.toAbsolutePath().getParent();
        }
        try (Stream<Path> siblings = Files.list(parent)) {
            return siblings
                    .filter(file -> file.getFileName().toString().startsWith(rootPattern))
                    .collect(Collectors.toList());
        }
    }

    private static boolean matchesMagic(Path path, int expected1, int expected2) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            int byte1 = fis.read();
            int byte2 = fis.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException e) {
            LOG.warning(e.getMessage());
        }
        return false;
    }

    private static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    @SuppressWarnings("java:S2095") // resources are closed via Stream.onClose()
    private static Stream<String> streamZipFile(Path path) throws IOException {
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
                LOG.warning(e.getMessage());
            }
        });
    }

    @SuppressWarnings("java:S2095") // resources are closed via Stream.onClose()
    private static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                LOG.warning(e.getMessage());
            }
        });
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    static String extractRootPattern(Path path) {
        String fileName = path.getFileName().toString();
        String[] parts = fileName.split("\\.");
        int baseLength;
        if ("current".equals(parts[parts.length - 1])) {
            baseLength = parts.length - 2;
        } else if (parts[parts.length - 1].matches("\\d+$")) {
            baseLength = parts.length - 1;
        } else {
            baseLength = parts.length;
        }
        StringBuilder base = new StringBuilder(parts[0]);
        for (int i = 1; i < baseLength; i++) {
            base.append(".").append(parts[i]);
        }
        return base.toString();
    }
}
