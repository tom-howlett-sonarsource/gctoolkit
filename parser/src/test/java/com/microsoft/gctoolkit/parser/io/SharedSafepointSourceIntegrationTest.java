package com.microsoft.gctoolkit.parser.io;

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

class SharedSafepointSourceIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void zipSkipsDirectoriesAndKeepsRawLinesWithoutSentinel() throws IOException {
        Path path = directory.resolve("safepoint.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/first.log"));
            output.write("  safepoint  \n\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("not selected\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (var lines = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("  safepoint  ", ""), lines.collect(Collectors.toList()));
        }
    }
}
