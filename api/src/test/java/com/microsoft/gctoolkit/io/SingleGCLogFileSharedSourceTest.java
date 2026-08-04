// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
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

class SingleGCLogFileSharedSourceTest {

    @TempDir
    Path directory;

    @Test
    void streamsTheFirstFileInAZipSource() throws IOException {
        Path path = directory.resolve("gc-source");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("[0.001s][info][gc] zip test\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        SingleGCLogFile logFile = new SingleGCLogFile(path);
        try (var lines = logFile.stream()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertTrue(collected.contains("[0.001s][info][gc] zip test"));
        }
        assertTrue(logFile.getMetaData().isZip());
        assertEquals(1, logFile.getMetaData().getNumberOfFiles());
    }
}
