// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Discovers readable logical sources in plain, ZIP, and GZIP GC log files.
 */
public final class LogFileSources {

    /** First magic byte in a GZIP file. */
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    /** Second magic byte in a GZIP file. */
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    /** First magic byte in a ZIP file. */
    private static final int ZIP_MAGIC_FIRST = 0x50;
    /** Second magic byte in a ZIP file. */
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private LogFileSources() {
    }

    /**
     * Detects a path's format from its type and magic bytes.
     * @param path path to inspect
     * @return detected format
     * @throws IOException when the path cannot be read
     */
    public static LogFileFormat format(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return LogFileFormat.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return LogFileFormat.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return LogFileFormat.ZIP;
            }
            return LogFileFormat.PLAIN_TEXT;
        }
    }

    /**
     * Discovers logical sources in a file or directory.
     * @param path file or directory to inspect
     * @return discovered sources in file or archive order
     * @throws IOException when a source cannot be inspected
     */
    public static List<LogFileSource> discover(final Path path)
            throws IOException {
        LogFileFormat format = format(path);
        switch (format) {
            case DIRECTORY:
                return discoverDirectory(path);
            case ZIP:
                return discoverZip(path);
            case GZIP:
                return List.of(new LogFileSource(path, format, null, -1));
            case PLAIN_TEXT:
                return List.of(new LogFileSource(
                        path, format, null, Files.size(path)));
            default:
                return List.of();
        }
    }

    /**
     * Lists regular files immediately within a directory.
     * @param directory directory to inspect
     * @return paths sorted by natural path order
     * @throws IOException when the directory cannot be read
     */
    public static List<Path> files(final Path directory) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(Files::isRegularFile)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Opens lines from the first source in a path.
     * @param path path to inspect
     * @return lines from the first source, or an empty stream
     * @throws IOException when the path cannot be read
     */
    public static Stream<String> lines(final Path path) throws IOException {
        List<LogFileSource> sources = discover(path);
        if (sources.isEmpty()) {
            return Stream.empty();
        }
        return sources.get(0).lines();
    }

    /**
     * Opens lines from a named source in a path.
     * @param path path to inspect
     * @param entryName source name to open
     * @return lines from the named source
     * @throws IOException when the source cannot be found or read
     */
    public static Stream<String> lines(final Path path,
                                       final String entryName)
            throws IOException {
        LogFileSource source = discover(path).stream()
                .filter(candidate -> candidate.name().equals(entryName))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Unable to find " + entryName + " in " + path));
        return source.lines();
    }

    private static List<LogFileSource> discoverDirectory(
            final Path directory) throws IOException {
        List<LogFileSource> sources = new ArrayList<>();
        for (Path file : files(directory)) {
            sources.addAll(discover(file));
        }
        return sources;
    }

    private static List<LogFileSource> discoverZip(final Path path)
            throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> new LogFileSource(
                            path, LogFileFormat.ZIP,
                            entry.getName(), entry.getSize()))
                    .collect(Collectors.toList());
        }
    }
}
