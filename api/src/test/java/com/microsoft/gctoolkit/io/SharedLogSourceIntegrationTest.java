package com.microsoft.gctoolkit.io;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedLogSourceIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void zipCallerRetainsNormalizationAndEndSentinel() throws IOException {
        Path path = directory.resolve("gc.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("  line  \n\n".getBytes(Charset.defaultCharset()));
            output.closeEntry();
        }
        SingleGCLogFile log = new SingleGCLogFile(path);
        assertTrue(log.getMetaData().isZip());
        assertEquals(1, log.getMetaData().getNumberOfFiles());
        try (var lines = log.stream()) {
            assertEquals(List.of("line", log.endOfData()), lines.collect(Collectors.toList()));
        }
    }
}
