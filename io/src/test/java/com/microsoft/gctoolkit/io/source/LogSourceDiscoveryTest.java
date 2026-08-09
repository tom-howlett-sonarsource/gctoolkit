// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceDiscoveryTest {

    @TempDir
    Path dir;

    @Test
    void listDirectoryReturnsAllChildren() throws IOException {
        Files.writeString(dir.resolve("a.log"), "a", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("b.log"), "b", StandardCharsets.UTF_8);
        List<String> names = LogSourceDiscovery.listDirectory(dir).stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of("a.log", "b.log"), names);
    }

    @Test
    void listSiblingsFiltersByPrefix() throws IOException {
        Path gcLog = dir.resolve("gc.log");
        Files.writeString(gcLog, "current", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("gc.log.0"), "0", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("gc.log.1"), "1", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("other.log"), "other", StandardCharsets.UTF_8);

        List<String> names = LogSourceDiscovery.listSiblingsStartingWith(gcLog, "gc.log").stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());

        assertEquals(3, names.size());
        assertTrue(names.contains("gc.log"));
        assertTrue(names.contains("gc.log.0"));
        assertTrue(names.contains("gc.log.1"));
        assertFalse(names.contains("other.log"));
    }

    @Test
    void listSiblingsHandlesRootlessPath() throws IOException {
        assertTrue(LogSourceDiscovery.listSiblingsStartingWith(Path.of("orphan"), "orphan").isEmpty());
    }
}
