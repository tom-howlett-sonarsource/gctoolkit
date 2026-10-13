package com.microsoft.gctoolkit.parser.io;

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

class SharedSafepointSourceTest {
    @TempDir
    Path directory;

    @Test
    void zipReadsOnlyFirstFileAndPreservesRawLinesWithoutSentinel() throws IOException {
        Path path = directory.resolve("safepoint.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("first"));
            output.write("  safepoint  \n\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (Stream<String> lines = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("  safepoint  ", ""), lines.collect(Collectors.toList()));
        }
    }
}
