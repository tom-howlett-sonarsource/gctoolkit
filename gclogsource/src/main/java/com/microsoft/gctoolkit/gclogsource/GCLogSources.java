// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovery utilities for GC log sources.
 */
public final class GCLogSources {

    private GCLogSources() {
    }

    /**
     * List files directly under a directory.
     *
     * @param directory directory to scan
     * @return child files
     * @throws IOException when the directory cannot be scanned
     */
    public static List<Path> filesIn(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * List sibling files whose names start with the given root pattern.
     *
     * @param path file path used to find the parent directory
     * @param rootPattern file name prefix
     * @return matching sibling files
     * @throws IOException when the parent directory cannot be scanned
     */
    public static List<Path> siblingFilesStartingWith(Path path, String rootPattern) throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }
        try (var files = Files.list(parent)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().startsWith(rootPattern))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * List non-directory entries in a ZIP source.
     *
     * @param path ZIP path
     * @return ZIP entry names
     * @throws IOException when the ZIP cannot be read
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (var zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
