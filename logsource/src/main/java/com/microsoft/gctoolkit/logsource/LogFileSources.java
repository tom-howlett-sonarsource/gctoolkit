// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

/**
 * Discovery of, and sizing of, the sources that make up a GC log. A log may be held in a
 * single file, in a set of files sharing a common root name, in a directory, or in the
 * entries of a ZIP file.
 */
public final class LogFileSources {

    private static final Logger LOG = Logger.getLogger(LogFileSources.class.getName());

    private LogFileSources() {
        // static utilities only
    }

    /**
     * Return the entries of a directory.
     *
     * @param directory The directory to be listed.
     * @return The entries of the directory.
     * @throws IOException Thrown if the directory cannot be listed.
     */
    public static List<Path> filesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.collect(toList());
        }
    }

    /**
     * Return the files that sit alongside the given path and whose file name starts with
     * the given prefix. This is how the segments of a rotating log are found when the log
     * is presented as one of its segments.
     *
     * @param path The path to a file in the directory to be searched.
     * @param prefix The prefix that the file name of a sibling must start with.
     * @return The siblings whose file name starts with the prefix.
     * @throws IOException Thrown if the containing directory cannot be listed.
     */
    public static List<Path> siblingsStartingWith(Path path, String prefix) throws IOException {
        try (Stream<Path> files = Files.list(path.getParent())) {
            return files
                    .filter(file -> file.getFileName().toString().startsWith(prefix))
                    .collect(toList());
        }
    }

    /**
     * Return the names of the entries of a ZIP source, in the order in which they are held
     * in the file. Directory entries are not included.
     *
     * @param path The path to the ZIP source.
     * @return The names of the entries in the ZIP source.
     * @throws IOException Thrown if the source cannot be read as a ZIP file.
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }

    /**
     * Return the size, in bytes, of the source. The size of a compressed source is the size
     * of the compressed file, not the size of the data it holds.
     *
     * @param path The path to the source.
     * @return The size of the source in bytes, or {@code 0} if the size cannot be determined.
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return 0L;
        }
    }
}
