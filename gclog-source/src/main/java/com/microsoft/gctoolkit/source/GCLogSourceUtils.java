// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filesystem operations shared by GC log data sources.
 */
public final class GCLogSourceUtils {

    private GCLogSourceUtils() {
    }

    /**
     * Discovers regular files represented by a file or directory source.
     *
     * @param source a regular file or directory containing log files
     * @return discovered files in deterministic path order
     * @throws IOException if the source cannot be read
     */
    public static List<Path> discover(Path source) throws IOException {
        return discover(source, path -> true);
    }

    /**
     * Discovers matching regular files represented by a file or directory source.
     *
     * @param source a regular file or directory containing log files
     * @param filter filter applied to discovered files
     * @return matching files in deterministic path order
     * @throws IOException if the source cannot be read
     */
    public static List<Path> discover(Path source, Predicate<Path> filter) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(filter, "filter");

        if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            return filter.test(source) ? List.of(source) : List.of();
        }
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("GC log source does not exist or is not a regular file or directory: " + source);
        }

        try (Stream<Path> entries = Files.list(source)) {
            return entries
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(filter)
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Calculates the number of bytes represented by a file or directory source.
     *
     * @param source a regular file or directory containing log files
     * @return total source size in bytes
     * @throws IOException if the source cannot be read or the total overflows a long
     */
    public static long size(Path source) throws IOException {
        return size(discover(source));
    }

    /**
     * Calculates the total number of bytes for distinct files.
     *
     * @param sources files to size
     * @return total size in bytes
     * @throws IOException if a file cannot be read or the total overflows a long
     */
    public static long size(Collection<Path> sources) throws IOException {
        Objects.requireNonNull(sources, "sources");
        long total = 0L;
        List<Path> distinctSources = sources.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .collect(Collectors.toList());
        for (Path source : distinctSources) {
            try {
                total = Math.addExact(total, Files.size(source));
            } catch (ArithmeticException arithmeticException) {
                throw new IOException("GC log source size exceeds the supported range", arithmeticException);
            }
        }
        return total;
    }
}
