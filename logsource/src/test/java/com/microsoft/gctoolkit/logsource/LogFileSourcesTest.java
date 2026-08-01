// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogFileSourcesTest {

    @TempDir
    Path directory;

    @Test
    void findsTheEntriesOfADirectory() throws IOException {
        LogSourceFixture.writePlainText(directory, "gc.log");
        LogSourceFixture.writePlainText(directory, "gc.log.0");
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(List.of("gc.log", "gc.log.0", "nested"), fileNames(LogFileSources.filesIn(directory)));
    }

    @Test
    void findsTheSegmentsOfARotatingLog() throws IOException {
        Path current = LogSourceFixture.writePlainText(directory, "gc.log");
        LogSourceFixture.writePlainText(directory, "gc.log.0");
        LogSourceFixture.writePlainText(directory, "gc.log.1");
        LogSourceFixture.writePlainText(directory, "safepoint.log");

        assertEquals(List.of("gc.log", "gc.log.0", "gc.log.1"),
                fileNames(LogFileSources.siblingsStartingWith(current, "gc.log")));
    }

    @Test
    void reportsADirectoryThatCannotBeListed() {
        Path missing = directory.resolve("does-not-exist");
        assertThrows(IOException.class, () -> LogFileSources.filesIn(missing));
    }

    @Test
    void findsTheEntriesOfAZipSourceInOrder() throws IOException {
        Path path = LogSourceFixture.writeZip(directory, "gc.zip", "gc.log.0", "gc.log.1", "gc.log");

        assertEquals(List.of("gc.log.0", "gc.log.1", "gc.log"), LogFileSources.zipEntryNames(path));
    }

    @Test
    void reportsASourceThatIsNotAZipFile() throws IOException {
        Path path = LogSourceFixture.writePlainText(directory, "gc.log");
        assertThrows(IOException.class, () -> LogFileSources.zipEntryNames(path));
    }

    @Test
    void sizesASource() throws IOException {
        Path path = LogSourceFixture.writePlainText(directory, "gc.log");
        assertEquals(LogSourceFixture.CONTENT.getBytes(StandardCharsets.UTF_8).length, LogFileSources.sizeInBytes(path));
    }

    @Test
    void sizesASourceThatCannotBeRead() {
        assertEquals(0L, LogFileSources.sizeInBytes(directory.resolve("does-not-exist.log")));
    }

    private List<String> fileNames(List<Path> paths) {
        return paths.stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
    }
}
