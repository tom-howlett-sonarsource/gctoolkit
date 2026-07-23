// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.core;

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
import java.util.zip.ZipInputStream;

/**
 * Shared utilities for GC log source discovery, format detection,
 * byte sizing, and opening plain-text, ZIP, and GZIP log streams.
 * <p>
 * This class is used by both the API and parser modules to avoid
 * duplicating low-level IO logic.
 */
public final class GCLogSourceUtil {

    private static final Logger LOGGER = Logger.getLogger(GCLogSourceUtil.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSourceUtil() {
        // utility class
    }

    /**
     * Detect the format of the file at the given path by inspecting
     * its magic bytes or checking whether it is a directory.
     *
     * @param path the path to inspect
     * @return the detected {@link FileFormat}
     */
    public static FileFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        }
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int byte1 = in.read();
            int byte2 = in.read();
            if (byte1 == GZIP_MAGIC1 && byte2 == GZIP_MAGIC2) {
                return FileFormat.GZIP;
            }
            if (byte1 == ZIP_MAGIC1 && byte2 == ZIP_MAGIC2) {
                return FileFormat.ZIP;
            }
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return FileFormat.PLAINTEXT;
    }

    /**
     * Open a plain-text log file as a stream of lines.
     *
     * @param path the path to the plain-text file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a ZIP-compressed log file and stream lines from the first
     * non-directory entry.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from the first entry
     * @throws IOException if the file cannot be read or contains no entries
     */
    @SuppressWarnings("resource") // Resource ownership transfers to the returned stream via onClose
    public static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipStream.close();
            throw new IOException("ZIP file contains no entries: " + path);
        }
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
     * Open a GZIP-compressed log file as a stream of lines.
     *
     * @param path the path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("resource") // Resource ownership transfers to the returned stream via onClose
    public static Stream<String> streamGZipFile(Path path) throws IOException {
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
     * Return the size of the file in bytes, or {@code -1} if the size
     * cannot be determined.
     *
     * @param path the path to the file
     * @return the file size in bytes, or -1 on failure
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
            return -1L;
        }
    }

    /**
     * Discover log source files within a directory that match a given
     * root pattern prefix.  If {@code rootPattern} is {@code null},
     * all regular files in the directory are returned.
     *
     * @param directory   the directory to search
     * @param rootPattern the filename prefix to match, or {@code null} for all files
     * @return a list of matching paths
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> discoverLogFiles(Path directory, String rootPattern) throws IOException {
        try (Stream<Path> listing = Files.list(directory)) {
            if (rootPattern == null) {
                return listing.collect(Collectors.toList());
            }
            return listing
                    .filter(file -> file.getFileName().toString().startsWith(rootPattern))
                    .collect(Collectors.toList());
        }
    }
}
