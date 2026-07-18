// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SafepointLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsZipLogThroughSharedSource() throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        SafepointLogFile logFile = new SafepointLogFile(path);

        try (var lines = logFile.stream()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }
}
