// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Source-discovery helpers: list candidate log files rooted at a directory or
 * next to a specific log file.
 */
public final class LogSources {

    private LogSources() {
    }

    /**
     * List every regular child of {@code directory}.
     */
    public static List<Path> listDirectory(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.collect(Collectors.toList());
        }
    }

    /**
     * List every sibling of {@code file} whose filename starts with
     * {@code baseName}. Used to gather rotating log segments that share a
     * common root name.
     */
    public static List<Path> listSiblingsWithPrefix(Path file, String baseName) throws IOException {
        try (var entries = Files.list(file.getParent())) {
            return entries
                    .filter(candidate -> candidate.getFileName().toString().startsWith(baseName))
                    .collect(Collectors.toList());
        }
    }
}
