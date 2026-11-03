package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogFileZipSegmentTest {

    @TempDir
    Path tempDir;

    @Test
    void closingStreamClosesZipResources() throws IOException {
        Path archive = tempDir.resolve("gc-log.zip");
        try (ZipOutputStream zipOutput = new ZipOutputStream(java.nio.file.Files.newOutputStream(archive))) {
            zipOutput.putNextEntry(new ZipEntry("gc.log"));
            zipOutput.write("first line\nsecond line\n".getBytes(StandardCharsets.UTF_8));
            zipOutput.closeEntry();
        }

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        try (Stream<String> lines = segment.stream()) {
            assertEquals(Arrays.asList("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }
}
