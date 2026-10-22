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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafepointLogFileZipTest {

    @TempDir
    Path directory;

    @Test
    void streamsZipSource() throws IOException {
        Path path = directory.resolve("safepoint.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("safepoint.log"));
            out.write("0.001: safepoint test\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> lines = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("0.001: safepoint test"), lines.collect(Collectors.toList()));
        }
    }
}
