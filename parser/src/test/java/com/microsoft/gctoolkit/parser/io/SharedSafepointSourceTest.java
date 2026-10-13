package com.microsoft.gctoolkit.parser.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedSafepointSourceTest {
    @TempDir
    Path directory;

    @Test
    void zipCallerRetainsRawLinesWithoutSentinel() throws IOException {
        Path path = directory.resolve("safepoint.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/safepoint.log"));
            output.write("  line  \n\n".getBytes(Charset.defaultCharset()));
            output.closeEntry();
        }
        try (var lines = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("  line  ", ""), lines.collect(Collectors.toList()));
        }
    }
}
