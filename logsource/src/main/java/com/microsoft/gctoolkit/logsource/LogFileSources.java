// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovery of, and sizing of, the sources that make up a GC log. A GC log may be a single file,
 * a set of rotating files found in a directory, or a set of entries within a ZIP file.
 */
public final class LogFileSources {

    private static final Logger LOG = Logger.getLogger(LogFileSources.class.getName());

    private LogFileSources() {
    }

    /**
     * The size, in bytes, of the source found at the given path. The size of a directory is the
     * sum of the sizes of the files it directly contains.
     * @param path The path to the source.
     * @return The size of the source in bytes, or {@code 0} if the size cannot be determined.
     */
    public static long byteSize(Path path) {
        if (path == null)
            return 0L;
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> contents = Files.list(path)) {
                    return contents.filter(Files::isRegularFile)
                            .mapToLong(LogFileSources::byteSize)
                            .sum();
                }
            }
            return Files.size(path);
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            LOG.warning("Unable to determine the size of " + path + ": " + ex.getMessage());
        }
        return 0L;
    }

    /**
     * The uncompressed size, in bytes, of an entry within a ZIP source.
     * @param path The path to the ZIP file.
     * @param entryName The name of the entry within the ZIP file.
     * @return The uncompressed size of the entry in bytes, or {@code 0} if it cannot be determined.
     */
    public static long entryByteSize(Path path, String entryName) {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                return 0L;
            long size = entry.getSize();
            return (size < 0L) ? 0L : size;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return 0L;
    }

    /**
     * The names of the entries, excluding directories, found in a ZIP source. The names are
     * returned in the order in which they appear in the ZIP file.
     * @param path The path to the ZIP file.
     * @return The names of the entries in the ZIP file.
     * @throws IOException Thrown if the ZIP file cannot be read.
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * The sources found directly within a directory.
     * @param directory The path to the directory.
     * @return The paths of the sources in the directory.
     * @throws IOException Thrown if the directory cannot be read.
     */
    public static List<Path> sourcesInDirectory(Path directory) throws IOException {
        try (Stream<Path> contents = Files.list(directory)) {
            return contents.collect(Collectors.toList());
        }
    }

    /**
     * The sources that sit alongside the given source and whose file name starts with the given
     * prefix. This is how the segments of a rotating log are found when the log is not contained
     * in a directory or a ZIP file.
     * @param source The path to a source in the directory to be searched.
     * @param prefix The prefix that the file name of a sibling source must start with.
     * @return The paths of the matching sources.
     * @throws IOException Thrown if the containing directory cannot be read.
     */
    public static List<Path> siblingSourcesWithPrefix(Path source, String prefix) throws IOException {
        try (Stream<Path> contents = Files.list(source.getParent())) {
            return contents
                    .filter(file -> file.getFileName().toString().startsWith(prefix))
                    .collect(Collectors.toList());
        }
    }
}
