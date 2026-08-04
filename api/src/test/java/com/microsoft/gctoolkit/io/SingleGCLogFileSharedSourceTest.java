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

class SingleGCLogFileSharedSourceTest {

    @TempDir
    Path directory;

    @Test
    void streamsTheFirstFileFromAZipSource() throws IOException {
        Path path = directory.resolve("gc.log.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("  [0.001s][info][gc] test  \n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        try (var lines = new SingleGCLogFile(path).stream()) {
            assertEquals(
                    List.of("[0.001s][info][gc] test", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }
}
