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

class SharedLogSourceIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void singleZipRetainsFilteringAndSentinelAndSegmentsReadNamedEntries() throws IOException {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("  [0.001s][info][gc] first  \n\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log.0"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        SingleGCLogFile log = new SingleGCLogFile(archive);
        assertTrue(log.getMetaData().isZip());
        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of("[0.001s][info][gc] first", log.endOfData()),
                    lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "logs/gc.log.0").stream()) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
    }
}
