// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleGCLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesZipStreamingBehavior() throws IOException {
        Path path = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("logs/gc.log"));
            zip.write(" first \n\nsecond\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        SingleGCLogFile logFile = new SingleGCLogFile(path);

        assertTrue(logFile.getMetaData().isZip());
        assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines(logFile));
    }

    @Test
    void preservesGZipStreamingBehavior() throws IOException {
        Path path = temporaryDirectory.resolve("gc.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("line\n".getBytes(StandardCharsets.UTF_8));
        }

        SingleGCLogFile logFile = new SingleGCLogFile(path);

        assertTrue(logFile.getMetaData().isGZip());
        assertEquals(List.of("line", GCLogFile.END_OF_DATA_SENTINEL), lines(logFile));
    }

    private List<String> lines(SingleGCLogFile logFile) throws IOException {
        try (Stream<String> stream = logFile.stream()) {
            return stream.collect(Collectors.toList());
        }
    }
}
