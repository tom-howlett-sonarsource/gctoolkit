// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Locates GC log test data and exposes information about the resolved source.
 */
public class GCLogSource {

    private static final Path PREUNIFIED_DIRECTORY = Path.of("preunified");
    private static final Path DETAILS_DIRECTORY = Path.of("details");
    private static final Path TENURING_DIRECTORY = Path.of("tenuring");

    private static final List<Path> LOG_DIRECTORIES = List.of(
            Path.of(""),
            PREUNIFIED_DIRECTORY,
            PREUNIFIED_DIRECTORY.resolve("cms").resolve("parnew").resolve(DETAILS_DIRECTORY).resolve(TENURING_DIRECTORY),
            PREUNIFIED_DIRECTORY.resolve("verbose").resolve(TENURING_DIRECTORY),
            PREUNIFIED_DIRECTORY.resolve("ps").resolve(DETAILS_DIRECTORY).resolve(TENURING_DIRECTORY),
            PREUNIFIED_DIRECTORY.resolve("ps").resolve(DETAILS_DIRECTORY),
            Path.of("unified"),
            Path.of("unified", "g1gc"),
            Path.of("streaming"),
            Path.of("safepoint"));

    private static final int MAXIMUM_PARENT_DEPTH = 2;

    private final Path path;

    /**
     * Locates a GC log relative to the current working directory.
     *
     * @param fileName GC log file name
     */
    public GCLogSource(String fileName) {
        this(find(Path.of(""), fileName).path);
    }

    /**
     * Uses an already resolved GC log file.
     *
     * @param file GC log file
     */
    public GCLogSource(File file) {
        this(Objects.requireNonNull(file, "file").toPath());
    }

    private GCLogSource(Path path) {
        this.path = path;
    }

    /**
     * Locates a GC log relative to a working directory.
     *
     * @param workingDirectory directory from which to search
     * @param fileName GC log file name
     * @return the resolved GC log source
     * @throws IllegalArgumentException when the GC log cannot be found
     */
    public static GCLogSource find(Path workingDirectory, String fileName) {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(fileName, "fileName");

        for (Path logDirectory : LOG_DIRECTORIES) {
            Path source = findFromAncestors(workingDirectory, logDirectory, fileName, false);
            if (source != null) {
                return new GCLogSource(source);
            }

            source = findFromAncestors(workingDirectory, logDirectory, fileName, true);
            if (source != null) {
                return new GCLogSource(source);
            }
        }

        throw new IllegalArgumentException(fileName + " not found");
    }

    private static Path findFromAncestors(Path workingDirectory, Path logDirectory, String fileName,
                                          boolean belowGCLogsDirectory) {
        Path ancestor = workingDirectory;
        for (int depth = 0; depth <= MAXIMUM_PARENT_DEPTH && ancestor != null; depth++) {
            Path baseDirectory = belowGCLogsDirectory ? ancestor.resolve("gclogs") : ancestor;
            Path candidate = baseDirectory.resolve(logDirectory).resolve(fileName).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            ancestor = ancestor.getParent();
        }
        return null;
    }

    /**
     * @return the resolved file path
     */
    public String getPath() {
        return path.toString();
    }

    /**
     * @return the resolved file
     */
    public File getFile() {
        return path.toFile();
    }

    /**
     * @return source size in bytes
     * @throws IOException when the source size cannot be read
     */
    public long size() throws IOException {
        return Files.size(path);
    }
}
