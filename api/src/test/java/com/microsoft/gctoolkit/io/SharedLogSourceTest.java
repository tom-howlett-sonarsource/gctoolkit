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

class SharedLogSourceTest {
    @TempDir
    Path directory;

    @Test
    void singleZipPreservesNormalizationFirstEntryAndSentinel() throws IOException {
        Path source = directory.resolve("gc.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/first.log"));
            output.write("  first  \n\n  second \n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        SingleGCLogFile log = new SingleGCLogFile(source);
        assertTrue(log.getMetaData().isZip());
        try (var lines = log.stream()) {
            assertEquals(List.of("first", "second", log.endOfData()), lines.collect(Collectors.toList()));
        }
        try (var lines = new GCLogFileZipSegment(source, "logs/ignored.log").stream()) {
            assertEquals(List.of("ignored"), lines.collect(Collectors.toList()));
        }
    }
}
