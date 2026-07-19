// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourcesTest {

    private static final String GC_LOG = "gc.log";
    private static final String OTHER_LOG = "other.log";

    @Test
    void listsAllChildrenOfDirectory(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.log"), "");
        Files.writeString(tempDir.resolve("b.log"), "");
        Files.createDirectory(tempDir.resolve("child"));

        List<Path> discovered = LogSources.listDirectory(tempDir);
        Set<String> names = discovered.stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("a.log", "b.log", "child"), names);
    }

    @Test
    void listsSiblingsWithMatchingPrefix(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(GC_LOG), "");
        Files.writeString(tempDir.resolve("gc.log.0"), "");
        Files.writeString(tempDir.resolve("gc.log.1.current"), "");
        Files.writeString(tempDir.resolve(OTHER_LOG), "");
        Path anchor = tempDir.resolve(GC_LOG);

        List<Path> discovered = LogSources.listSiblingsWithPrefix(anchor, "gc");
        Set<String> names = discovered.stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of(GC_LOG, "gc.log.0", "gc.log.1.current"), names);
    }

    @Test
    void listsSiblingsWithPrefixReturnsEmptyWhenNoneMatch(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(OTHER_LOG), "");
        Path anchor = tempDir.resolve(OTHER_LOG);
        List<Path> discovered = LogSources.listSiblingsWithPrefix(anchor, "no-match");
        assertEquals(List.of(), discovered);
    }
}
