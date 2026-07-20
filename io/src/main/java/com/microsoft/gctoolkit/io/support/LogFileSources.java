// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Enumerates the GC log source files reachable from a filesystem path or a
 * ZIP archive. Used to discover the segments of a rotating log.
 */
public final class LogFileSources {

    private LogFileSources() {
    }

    /**
     * List the immediate entries of a directory. The order returned matches
     * {@link Files#list(Path)}.
     *
     * @param directory directory to list
     * @return the immediate entries
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> listDirectory(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    /**
     * List the immediate entries of a directory whose file name starts with
     * the given prefix.
     *
     * @param directory directory to list
     * @param prefix    required file-name prefix
     * @return matching entries
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> listDirectoryStartingWith(Path directory, String prefix) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(prefix, "prefix");
        Predicate<Path> matchesPrefix = p -> p.getFileName().toString().startsWith(prefix);
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(matchesPrefix).collect(Collectors.toList());
        }
    }

    /**
     * Return the names of the non-directory entries of a ZIP archive, in the
     * order the archive lists them.
     *
     * @param archive path to the ZIP archive
     * @return entry names
     * @throws IOException if the archive cannot be opened
     */
    public static List<String> listZipEntryNames(Path archive) throws IOException {
        Objects.requireNonNull(archive, "archive");
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

}
