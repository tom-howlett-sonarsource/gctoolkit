// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceUtilsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversARegularFile() throws IOException {
        Path log = write("gc.log", "abc");

        assertEquals(List.of(log), GCLogSourceUtils.discover(log));
    }

    @Test
    void discoversMatchingRegularFilesInNameOrder() throws IOException {
        Path second = write("gc.log.2", "second");
        Path first = write("gc.log.1", "first");
        Files.createDirectory(temporaryDirectory.resolve("gc.log.3"));

        assertEquals(List.of(first, second),
                GCLogSourceUtils.discover(temporaryDirectory,
                        path -> path.getFileName().toString().startsWith("gc.log.")));
    }

    @Test
    void rejectsMissingSources() {
        Path missing = temporaryDirectory.resolve("missing.log");

        assertThrows(IOException.class, () -> GCLogSourceUtils.discover(missing));
    }

    @Test
    void sizesFilesAndDirectories() throws IOException {
        Path first = write("gc.log.1", "1234");
        Path second = write("gc.log.2", "123456");

        assertEquals(4L, GCLogSourceUtils.size(first));
        assertEquals(10L, GCLogSourceUtils.size(temporaryDirectory));
        assertEquals(10L, GCLogSourceUtils.size(List.of(first, second, first)));
    }

    private Path write(String fileName, String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(fileName), content);
    }
}
