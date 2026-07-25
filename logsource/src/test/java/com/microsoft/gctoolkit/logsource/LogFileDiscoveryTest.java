// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LogFileDiscoveryTest {

    private static final List<String> LINES = List.of("first", "second");
    private static final String ROOT = "gc.log";
    private static final String FIRST_SEGMENT = "gc.log.0";
    private static final String SECOND_SEGMENT = "gc.log.1";

    @TempDir
    Path directory;

    @Test
    public void everyFileInADirectoryIsDiscovered() throws IOException {
        LogSourceTestFiles.plainText(directory, ROOT, LINES);
        LogSourceTestFiles.plainText(directory, SECOND_SEGMENT, LINES);

        assertEquals(List.of(ROOT, SECOND_SEGMENT), fileNames(LogFileDiscovery.directoryContents(directory)));
    }

    @Test
    public void onlySiblingsSharingTheRootAreDiscovered() throws IOException {
        Path log = LogSourceTestFiles.plainText(directory, ROOT, LINES);
        LogSourceTestFiles.plainText(directory, SECOND_SEGMENT, LINES);
        LogSourceTestFiles.plainText(directory, "safepoint.log", LINES);

        assertEquals(List.of(ROOT, SECOND_SEGMENT), fileNames(LogFileDiscovery.siblingsWithPrefix(log, ROOT)));
    }

    @Test
    public void zipEntryNamesSkipDirectoryEntries() throws IOException {
        Path path = LogSourceTestFiles.zip(directory, "rotating.zip", List.of(FIRST_SEGMENT, SECOND_SEGMENT));

        assertEquals(List.of(FIRST_SEGMENT, SECOND_SEGMENT), LogFileDiscovery.zipEntryNames(path));
    }

    @Test
    public void discoveringInAMissingDirectoryFails() {
        Path missing = directory.resolve("missing");
        assertThrows(IOException.class, () -> LogFileDiscovery.directoryContents(missing));
    }

    @Test
    public void discoveringSiblingsOfAMissingDirectoryFails() throws IOException {
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Path log = nested.resolve(ROOT);
        Files.delete(nested);

        assertThrows(IOException.class, () -> LogFileDiscovery.siblingsWithPrefix(log, ROOT));
    }

    private List<String> fileNames(List<Path> paths) {
        return paths.stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .sorted()
                .collect(Collectors.toList());
    }
}
