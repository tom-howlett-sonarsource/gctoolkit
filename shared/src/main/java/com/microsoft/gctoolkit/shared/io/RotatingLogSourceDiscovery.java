// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovers and orders rotating GC log source segments.
 */
public final class RotatingLogSourceDiscovery {

    private static final Logger LOG = Logger.getLogger(RotatingLogSourceDiscovery.class.getName());

    private RotatingLogSourceDiscovery() {}

    public static <T extends LogSourceSegment> List<T> discover(
            Path path,
            LogSourceFormat format,
            Function<Path, T> fileSegmentFactory,
            BiFunction<Path, String, T> zipSegmentFactory) {

        List<T> segments = new ArrayList<>();
        if (format == LogSourceFormat.ZIP) {
            findZipSegments(path, zipSegmentFactory, segments);
        } else if (format == LogSourceFormat.PLAINTEXT || format == LogSourceFormat.DIRECTORY) {
            findFileSegments(path, format, fileSegmentFactory, segments);
        } else {
            LOG.warning("unknown log file format");
        }
        return orderSegments(path, format, segments);
    }

    private static <T extends LogSourceSegment> void findZipSegments(
            Path path,
            BiFunction<Path, String, T> zipSegmentFactory,
            List<T> segments) {
        try (var zipfile = new ZipFile(path.toFile())) {
            zipfile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .map(name -> zipSegmentFactory.apply(path, name))
                    .forEach(segments::add);
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
    }

    private static <T extends LogSourceSegment> void findFileSegments(
            Path path,
            LogSourceFormat format,
            Function<Path, T> fileSegmentFactory,
            List<T> segments) {
        try {
            if (format == LogSourceFormat.DIRECTORY) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.map(fileSegmentFactory).forEach(segments::add);
                }
            } else {
                String rootPattern = getRootPattern(path, format, segments);
                try (Stream<Path> stream = Files.list(path.getParent())) {
                    stream
                            .filter(file -> file.getFileName().toString().startsWith(rootPattern))
                            .map(fileSegmentFactory)
                            .forEach(segments::add);
                }
            }
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to find log segments.", ioe);
        }
    }

    private static <T extends LogSourceSegment> List<T> orderSegments(Path path, LogSourceFormat format, List<T> segments) {
        if (segments.size() < 2)
            return segments;

        LinkedList<T> orderedList = new LinkedList<>();
        List<T> workingList = new ArrayList<>(segments);
        String basePattern = getRootPattern(path, format, segments);
        T current = workingList.stream()
                .filter(segment -> segment.getSegmentName().endsWith(basePattern) || segment.getSegmentName().endsWith(".current"))
                .findFirst()
                .orElse(workingList.get(0));

        orderedList.addLast(current);
        workingList = removeIneligibleSegments(workingList, current);
        while (!workingList.isEmpty()) {
            current = workingList.stream()
                    .max(Comparator.comparing(LogSourceSegment::getEndTime))
                    .orElseThrow();
            orderedList.addFirst(current);
            workingList = removeIneligibleSegments(workingList, current);
        }
        return orderedList;
    }

    private static <T extends LogSourceSegment> List<T> removeIneligibleSegments(List<T> logFileSegments, T current) {
        return logFileSegments.stream()
                .filter(segment -> segment.getEndTime() <= current.getStartTime())
                .collect(Collectors.toList());
    }

    private static <T extends LogSourceSegment> String getRootPattern(
            Path path,
            LogSourceFormat format,
            List<T> segments) {

        String[] bits;
        if (format == LogSourceFormat.DIRECTORY) {
            bits = segments.stream()
                    .filter(segment -> !segment.getSegmentName().matches(".+\\.\\d+$"))
                    .findFirst()
                    .map(LogSourceSegment::getSegmentName)
                    .orElse(path.getFileName().toString())
                    .split("\\.");
        } else if (format == LogSourceFormat.ZIP && !segments.isEmpty()) {
            bits = segments.get(0).getSegmentName().split("\\.");
        } else {
            bits = path.getFileName().toString().split("\\.");
        }

        int baseLength;
        if ("current".equals(bits[bits.length - 1]))
            baseLength = bits.length - 2;
        else if (bits[bits.length - 1].matches("\\d+$"))
            baseLength = bits.length - 1;
        else
            baseLength = bits.length;

        StringBuilder base = new StringBuilder(bits[0]);
        for (int i = 1; i < baseLength; i++)
            base.append(".").append(bits[i]);
        return base.toString();
    }
}
