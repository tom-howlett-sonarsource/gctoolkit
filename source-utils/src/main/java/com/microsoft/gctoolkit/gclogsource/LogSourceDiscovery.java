// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shared discovery operations for single and rotating GC log sources.
 */
public final class LogSourceDiscovery {

    public static final String ROTATING_LOG_SUFFIX = ".*\\.(\\d+)(\\.current)?$";
    public static final Pattern ROTATING_LOG_PATTERN = Pattern.compile(ROTATING_LOG_SUFFIX);

    private LogSourceDiscovery() {
    }

    public static List<Path> discoverDirectorySegments(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public static List<Path> discoverSiblingSegments(Path path, String rootPattern) throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }
        try (var files = Files.list(parent)) {
            return files
                    .filter(file -> file.getFileName().toString().startsWith(rootPattern))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public static List<String> discoverZipSegments(Path zipPath) throws IOException {
        try (var zipFile = new ZipFile(zipPath.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public static String rootPattern(Path path, LogSourceFormat format, List<String> segmentNames) {
        String segmentName;
        if (format == LogSourceFormat.DIRECTORY) {
            segmentName = segmentNames.stream()
                    .filter(name -> !name.matches(".+\\.\\d+$"))
                    .findFirst()
                    .orElseGet(() -> firstSegmentName(segmentNames));
        } else if (format == LogSourceFormat.ZIP) {
            segmentName = firstSegmentName(segmentNames);
        } else {
            segmentName = path.getFileName().toString();
        }
        return rootPattern(segmentName);
    }

    public static String rootPattern(String segmentName) {
        String[] bits = segmentName.split("\\.");
        int baseLength = bits.length;
        if ("current".equals(bits[bits.length - 1])) {
            baseLength = bits.length - 2;
        } else if (bits[bits.length - 1].matches("\\d+$")) {
            baseLength = bits.length - 1;
        }
        StringBuilder base = new StringBuilder(bits[0]);
        for (int i = 1; i < baseLength; i++) {
            base.append(".").append(bits[i]);
        }
        return base.toString();
    }

    private static String firstSegmentName(List<String> segmentNames) {
        return segmentNames.stream()
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalArgumentException("No log segments found"));
    }
}
