package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedLogSourceTest {
    @TempDir
    Path directory;

    @Test
    void singleZipSkipsDirectoriesFiltersLinesAndAddsSentinel() throws IOException {
        Path zip = archive();
        SingleGCLogFile log = new SingleGCLogFile(zip);
        assertTrue(log.getMetaData().isZip());
        assertEquals(1, log.getMetaData().getNumberOfFiles());
        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of("2.000: first", "3.000: end", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentsAndRotatingSourcesRetainTheirEntrySemantics() throws IOException {
        Path zip = archive();
        try (Stream<String> lines = new GCLogFileZipSegment(zip, "gc.log.1").stream()) {
            assertEquals(List.of("0.500: second", "1.000: end"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new RotatingGCLogFile(zip).stream()) {
            assertEquals(List.of("0.500: second", "1.000: end", "2.000: first", "3.000: end",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    private Path archive() throws IOException {
        Path path = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write("2.000: first  \n\n3.000: end\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.1"));
            output.write("0.500: second\n1.000: end\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}
