// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Locates GC logs used by tests from a module or repository working directory.
 */
public final class TestLogFile {

    /** Directories that can contain test GC logs. */
    private static final List<String> LOG_DIRECTORIES = List.of(
            "",
            "preunified",
            "preunified/cms/parnew/details/tenuring",
            "preunified/verbose/tenuring",
            "preunified/ps/details/tenuring",
            "preunified/ps/details",
            "unified",
            "unified/g1gc",
            "streaming",
            "safepoint"
    );

    /** Roots used when tests run from the repository or a module directory. */
    private static final List<Path> SEARCH_ROOTS = List.of(
            Path.of("."),
            Path.of(".."),
            Path.of("../.."),
            Path.of("./gclogs"),
            Path.of("../gclogs"),
            Path.of("../../gclogs")
    );

    /** The resolved GC log file. */
    private final File logFile;

    /**
     * Locates a GC log by name.
     *
     * @param fileName GC log name or relative path
     * @throws IllegalArgumentException if the GC log cannot be found
     */
    public TestLogFile(final String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        logFile = candidates(fileName)
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        fileName + " not found"));
    }

    /**
     * Wraps a resolved GC log file.
     *
     * @param file GC log file
     */
    public TestLogFile(final File file) {
        logFile = Objects.requireNonNull(file, "file");
    }

    /**
     * Returns the GC log path.
     *
     * @return GC log path
     */
    public String getPath() {
        return logFile.getPath();
    }

    /**
     * Returns the GC log file.
     *
     * @return GC log file
     */
    public File getFile() {
        return logFile;
    }

    /**
     * Returns the GC log size in bytes.
     *
     * @return GC log size in bytes
     * @throws IOException if the file size cannot be read
     */
    public long getSize() throws IOException {
        return Files.size(logFile.toPath());
    }

    /**
     * Builds the candidate paths for a GC log name.
     *
     * @param fileName GC log name or relative path
     * @return candidate paths
     */
    private static Stream<Path> candidates(final String fileName) {
        Path requestedPath = Path.of(fileName);
        return LOG_DIRECTORIES.stream()
                .flatMap(directory -> SEARCH_ROOTS.stream()
                        .map(root -> root.resolve(directory)
                                .resolve(requestedPath)
                                .normalize()));
    }
}
