// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileSourcesTest {

    @TempDir
    Path directory;

    @Test
    void byteSizeOfAPlainTextSource() throws IOException {
        Path path = TestSources.plainText(directory, "gc.log");
        assertEquals(TestSources.CONTENT.getBytes(StandardCharsets.UTF_8).length, LogFileSources.byteSize(path));
    }

    @Test
    void byteSizeOfADirectoryIsTheSumOfItsFiles() throws IOException {
        TestSources.plainText(directory, "gc.log.0", "one\n");
        TestSources.plainText(directory, "gc.log.1", "two\n");
        assertEquals(8L, LogFileSources.byteSize(directory));
    }

    @Test
    void byteSizeOfAnUnreadableSourceIsZero() {
        assertEquals(0L, LogFileSources.byteSize(directory.resolve("absent.log")));
        assertEquals(0L, LogFileSources.byteSize(null));
    }

    @Test
    void byteSizeOfAZipEntry() throws IOException {
        Path zip = TestSources.zip(directory, "gc.zip", "gc.log");
        assertEquals(TestSources.CONTENT.getBytes(StandardCharsets.UTF_8).length,
                LogFileSources.entryByteSize(zip, "gc.log"));
        assertEquals(0L, LogFileSources.entryByteSize(zip, "not-an-entry.log"));
    }

    @Test
    void zipEntryNamesAreDiscoveredInOrder() throws IOException {
        Path zip = TestSources.zip(directory, "rotating.zip", "gc.log.0", "gc.log.1");
        assertEquals(List.of("gc.log.0", "gc.log.1"), LogFileSources.zipEntryNames(zip));
    }

    @Test
    void sourcesInADirectoryAreDiscovered() throws IOException {
        TestSources.plainText(directory, "gc.log.0");
        TestSources.plainText(directory, "gc.log.1");
        List<String> names = LogFileSources.sourcesInDirectory(directory).stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of("gc.log.0", "gc.log.1"), names);
    }

    @Test
    void siblingSourcesAreDiscoveredByPrefix() throws IOException {
        Path source = TestSources.plainText(directory, "gc.log");
        TestSources.plainText(directory, "gc.log.1");
        TestSources.plainText(directory, "other.log");
        List<String> names = LogFileSources.siblingSourcesWithPrefix(source, "gc.log").stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of("gc.log", "gc.log.1"), names);
        assertTrue(LogFileSources.siblingSourcesWithPrefix(source, "no-such-root").isEmpty());
    }
}
