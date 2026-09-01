// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogFileZipSegmentTest {

    private static final String SEGMENT_NAME = "gc.log.0";

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsNamedZipSegment() throws IOException {
        Path zipFile = zipWithSegment("0.100: first\n0.200: second\n");

        GCLogFileZipSegment segment = new GCLogFileZipSegment(zipFile, SEGMENT_NAME);

        try (Stream<String> lines = segment.stream()) {
            assertEquals(List.of("0.100: first", "0.200: second"), lines.collect(toList()));
        }
    }

    @Test
    void reportsStartAndEndTimesFromSegmentContent() throws IOException {
        Path zipFile = zipWithSegment("0.100: first\n0.200: second\n");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zipFile, SEGMENT_NAME);

        assertEquals(0.100d, segment.getStartTime());
        assertEquals(0.200d, segment.getEndTime());
    }

    @Test
    void returnsEmptyStreamForMissingZipSegment() throws IOException {
        Path zipFile = zipWithSegment("0.100: first\n");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zipFile, "missing.log");

        try (Stream<String> lines = segment.stream()) {
            assertEquals(List.of(), lines.collect(toList()));
        }
    }

    private Path zipWithSegment(String content) throws IOException {
        Path zipFile = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zipOutputStream.putNextEntry(new ZipEntry(SEGMENT_NAME));
            zipOutputStream.write(content.getBytes());
            zipOutputStream.closeEntry();
        }
        return zipFile;
    }
}
