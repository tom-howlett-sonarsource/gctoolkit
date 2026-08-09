// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * File-system utilities used to discover GC log source files that belong
 * together (e.g. the members of a rotating log set).
 */
public final class LogSourceDiscovery {

    private LogSourceDiscovery() {
    }

    /**
     * List the immediate contents of {@code directory}.
     *
     * @throws IOException if the directory cannot be listed.
     */
    public static List<Path> listDirectory(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.collect(Collectors.toList());
        }
    }

    /**
     * List the siblings of {@code file} whose file names start with the
     * given {@code rootName}. Useful for discovering rotating log
     * segments that share a common file-name prefix.
     *
     * @throws IOException if the parent directory cannot be listed.
     */
    public static List<Path> listSiblingsStartingWith(Path file, String rootName) throws IOException {
        Path parent = file.getParent();
        if (parent == null)
            return List.of();
        try (var stream = Files.list(parent)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(rootName))
                    .collect(Collectors.toList());
        }
    }
}
