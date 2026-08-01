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
 * Discovers the individual sources that make up a GC log, and reports how big they are.
 * <p>
 * A GC log may be a single file, a directory of rotating files, a set of sibling files sharing a
 * common root name, or the entries of a ZIP archive. Every method here returns a fully realised
 * collection so that no file handle outlives the call.
 */
public final class LogFileSources {

    private static final long UNKNOWN_SIZE = -1L;

    private LogFileSources() {
    }

    /**
     * Find the sources held directly in a directory.
     *
     * @param directory The directory to look in.
     * @return The paths found in the directory.
     * @throws IOException If the directory cannot be listed.
     */
    public static List<Path> filesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.collect(toList());
        }
    }

    /**
     * Find the sources sitting alongside the given path whose file name starts with the given root.
     * This is how the segments of a rotating log are found when the log is named by one of its
     * segments rather than by the directory holding them.
     *
     * @param path A path to one of the sources; its parent directory is searched.
     * @param root The root that the file name of a source must start with.
     * @return The matching paths found next to the given path.
     * @throws IOException If the parent directory cannot be listed.
     */
    public static List<Path> siblingsStartingWith(Path path, String root) throws IOException {
        try (Stream<Path> files = Files.list(path.getParent())) {
            return files
                    .filter(file -> file.getFileName().toString().startsWith(root))
                    .collect(toList());
        }
    }

    /**
     * Find the names of the entries of a ZIP archive that are not directories.
     *
     * @param path The path to the ZIP archive.
     * @return The names of the entries, in the order the archive lists them.
     * @throws IOException If the archive cannot be read.
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }

    /**
     * Report the size, in bytes, of a source.
     *
     * @param path The path to the log source.
     * @return The size of the source in bytes.
     * @throws IOException If the size cannot be determined.
     */
    public static long sizeInBytes(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Report the uncompressed size, in bytes, of an entry of a ZIP archive.
     *
     * @param path The path to the ZIP archive.
     * @param entryName The name of the entry within the archive.
     * @return The uncompressed size of the entry in bytes, or {@code -1} if the archive holds no
     * such entry or the archive does not record a size for it.
     * @throws IOException If the archive cannot be read.
     */
    public static long sizeInBytes(Path path, String entryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            return (entry == null) ? UNKNOWN_SIZE : entry.getSize();
        }
    }
}
