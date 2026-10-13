package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedSourceGCLogFileTest {
    @TempDir
    Path directory;

    @Test
    void singleZipSkipsDirectoriesAndPreservesFilteringAndSentinel() throws IOException {
        Path archive = directory.resolve("gc.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("  first  \n\n \nsecond\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        SingleGCLogFile log = new SingleGCLogFile(archive);
        assertTrue(log.getMetaData().isZip());
        try (var lines = log.stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        try (var lines = new GCLogFileZipSegment(archive, "ignored.log").stream()) {
            assertEquals(List.of("ignored"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipReadsAllEntriesAndAppendsOneSentinel() throws IOException {
        Path archive = directory.resolve("rotating.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write("3.000: current start  \n4.000: current end\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write("\n1.000: previous start\n2.000: previous end\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (var lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of("1.000: previous start", "2.000: previous end",
                            "3.000: current start", "4.000: current end", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }
}
