// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogFileZipSegmentTest {

    private static final String CONTENTS = "[0.100s][info][gc] first\n[0.200s][info][gc] second\n";

    @TempDir
    Path directory;

    @Test
    void theNamedEntryIsStreamedLineByLine() throws IOException {
        Path archive = archive();

        assertEquals(Arrays.asList("[0.100s][info][gc] first", "[0.200s][info][gc] second"),
                collect(new GCLogFileZipSegment(archive, "gc.log")));
    }

    @Test
    void theSegmentIsBoundedByTheTimesFoundInIt() throws IOException {
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive(), "gc.log");

        assertEquals(0.100d, segment.getStartTime(), 0.001d);
        assertEquals(0.200d, segment.getEndTime(), 0.001d);
        assertEquals("gc.log", segment.getSegmentName());
    }

    @Test
    void aMissingEntryStreamsNothing() throws IOException {
        assertEquals(Collections.emptyList(), collect(new GCLogFileZipSegment(archive(), "absent.log")));
    }

    @Test
    void anArchiveThatCannotBeOpenedStreamsNothing() throws IOException {
        Path notAnArchive = directory.resolve("gc.log");
        Files.write(notAnArchive, "not an archive".getBytes(StandardCharsets.UTF_8));

        assertEquals(Collections.emptyList(), collect(new GCLogFileZipSegment(notAnArchive, "gc.log")));
    }

    private Path archive() throws IOException {
        Path archive = directory.resolve("segments.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("gc.log"));
            zip.write(CONTENTS.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }

    private List<String> collect(GCLogFileZipSegment segment) {
        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = segment.stream()) {
            stream.forEach(lines::add);
        }
        return lines;
    }
}
