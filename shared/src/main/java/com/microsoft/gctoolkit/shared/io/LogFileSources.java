// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utilities for discovering related GC log sources.
 */
public final class LogFileSources {

    private LogFileSources() {
    }

    /**
     * Finds direct directory entries or siblings matching a rotating-log prefix.
     *
     * @param source a directory or one member of a rotating log set
     * @param fileNamePrefix prefix used when {@code source} is a file
     * @return discovered paths
     * @throws IOException if the containing directory cannot be listed
     */
    public static List<Path> discover(Path source, String fileNamePrefix) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fileNamePrefix, "fileNamePrefix");
        Path directory = Files.isDirectory(source) ? source : source.getParent();
        if (directory == null) {
            directory = source.toAbsolutePath().getParent();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            Stream<Path> discovered = Files.isDirectory(source)
                    ? paths
                    : paths.filter(path -> path.getFileName().toString().startsWith(fileNamePrefix));
            return discovered.collect(Collectors.toList());
        }
    }
}
