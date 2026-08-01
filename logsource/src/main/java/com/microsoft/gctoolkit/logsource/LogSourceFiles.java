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
 * Discovery of, and sizing for, the files that make up a GC log source. The methods in this class
 * consume the underlying directory or archive before they return so that the caller is never left
 * holding an open file handle.
 */
public final class LogSourceFiles {

    private LogSourceFiles() {
        // static utility
    }

    /**
     * List the files found in a directory. The order in which the files are listed is the order in
     * which the file system reports them.
     *
     * @param directory The directory to list.
     * @return The contents of the directory.
     * @throws IOException if the directory cannot be listed.
     */
    public static List<Path> filesIn(Path directory) throws IOException {
        try (Stream<Path> contents = Files.list(directory)) {
            return contents.collect(toList());
        }
    }

    /**
     * Find the files that sit alongside the given file and whose names start with the given prefix.
     * This is how the segments of a rotating log are discovered when the path given by the user is
     * one of those segments.
     *
     * @param path   A file in the directory to be searched.
     * @param prefix The prefix that the name of a sibling must start with.
     * @return The siblings of {@code path}, including {@code path} itself if its name starts with
     * the prefix.
     * @throws IOException if the containing directory cannot be listed.
     */
    public static List<Path> siblingsStartingWith(Path path, String prefix) throws IOException {
        try (Stream<Path> siblings = Files.list(path.getParent())) {
            return siblings
                    .filter(sibling -> sibling.getFileName().toString().startsWith(prefix))
                    .collect(toList());
        }
    }

    /**
     * List the names of the entries in a Zip archive. Directory entries are not included.
     *
     * @param path The path to the Zip archive.
     * @return The names of the entries in the archive.
     * @throws IOException if the archive cannot be read.
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
     * Return the size, in bytes, of a log source. The size of a directory is the sum of the sizes
     * of the regular files that it contains. The size reported for a compressed source is the size
     * of the archive, not the size of the log it contains.
     *
     * @param path The path to the log source.
     * @return The number of bytes in the source.
     * @throws IOException if the size of the source cannot be determined.
     */
    public static long sizeInBytes(Path path) throws IOException {
        if (!Files.isDirectory(path))
            return Files.size(path);

        long total = 0L;
        for (Path file : filesIn(path)) {
            if (Files.isRegularFile(file))
                total += Files.size(file);
        }
        return total;
    }
}
