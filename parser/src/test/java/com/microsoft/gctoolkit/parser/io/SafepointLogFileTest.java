package com.microsoft.gctoolkit.parser.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafepointLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsGzipLog() throws IOException {
        Path gzip = temporaryDirectory.resolve("safepoint.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        try (var lines = new SafepointLogFile(gzip).stream()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }
}
