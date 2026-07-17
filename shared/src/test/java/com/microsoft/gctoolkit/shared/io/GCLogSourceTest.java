// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void findsLogBelowGclogsDirectory() throws IOException {
        Path log = temporaryDirectory.resolve("gclogs/unified/g1gc/sample.log");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "sample");

        GCLogSource source = GCLogSource.find(temporaryDirectory, "sample.log");

        assertEquals(log, source.getFile().toPath());
        assertEquals(log.toString(), source.getPath());
    }

    @Test
    void searchesParentDirectories() throws IOException {
        Path log = temporaryDirectory.resolve("gclogs/preunified/sample.log");
        Path workingDirectory = temporaryDirectory.resolve("module/target");
        Files.createDirectories(log.getParent());
        Files.createDirectories(workingDirectory);
        Files.writeString(log, "sample");

        GCLogSource source = GCLogSource.find(workingDirectory, "sample.log");

        assertEquals(log, source.getFile().toPath());
    }

    @Test
    void reportsFileSize() throws IOException {
        Path log = temporaryDirectory.resolve("sample.log");
        Files.writeString(log, "123456789");

        GCLogSource source = new GCLogSource(log.toFile());

        assertEquals(9L, source.size());
    }

    @Test
    void rejectsMissingLog() {
        assertThrows(IllegalArgumentException.class,
                () -> GCLogSource.find(temporaryDirectory, "missing.log"));
    }
}
