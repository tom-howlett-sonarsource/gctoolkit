// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

/**
 * Discovers the log files that make up a GC log source. A source may be a directory of log file
 * segments, a set of sibling files sharing a rotating log name, or a ZIP archive of log files.
 */
public final class LogFileDiscovery {

    private LogFileDiscovery() {}

    /**
     * Return every entry held directly in the given directory.
     * @param directory The directory to list.
     * @return The paths of the entries in the directory.
     * @throws IOException Thrown if the directory cannot be listed.
     */
    public static List<Path> directoryContents(Path directory) throws IOException {
        try (Stream<Path> contents = Files.list(directory)) {
            return contents.collect(toList());
        }
    }

    /**
     * Return the entries alongside the given path whose file name starts with the given prefix.
     * Rotating log file segments are found by matching the root of the log file name.
     * @param path The path to a log file whose siblings are of interest.
     * @param prefix The prefix that a sibling file name must start with.
     * @return The paths of the matching siblings, including the given path when it matches.
     * @throws IOException Thrown if the containing directory cannot be listed.
     */
    public static List<Path> siblingsWithPrefix(Path path, String prefix) throws IOException {
        try (Stream<Path> contents = Files.list(path.getParent())) {
            return contents
                    .filter(sibling -> sibling.getFileName().toString().startsWith(prefix))
                    .collect(toList());
        }
    }

    /**
     * Return the names of the log files held in a ZIP archive, in the order they appear in the
     * archive. Directory entries are not log files, so they are not returned.
     * @param path The path to the ZIP archive.
     * @return The names of the entries in the archive.
     * @throws IOException Thrown if the archive cannot be read.
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }
}
