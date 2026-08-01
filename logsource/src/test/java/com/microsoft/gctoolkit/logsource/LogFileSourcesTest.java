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
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogFileSourcesTest {

    @TempDir
    Path directory;

    @Test
    void findsEverySourceInADirectory() throws IOException {
        LogSourceFixture.plainText(directory, "gc.log");
        LogSourceFixture.plainText(directory, "gc.log.1");

        assertEquals(List.of("gc.log", "gc.log.1"), namesOf(LogFileSources.filesIn(directory)));
    }

    @Test
    void findsSiblingsSharingARootName() throws IOException {
        Path current = LogSourceFixture.plainText(directory, "gc.log");
        LogSourceFixture.plainText(directory, "gc.log.1");
        LogSourceFixture.plainText(directory, "gc.log.2");
        LogSourceFixture.plainText(directory, "other.log");

        assertEquals(List.of("gc.log", "gc.log.1", "gc.log.2"),
                namesOf(LogFileSources.siblingsStartingWith(current, "gc.log")));
    }

    @Test
    void findsTheEntriesOfAZipSourceButNotItsDirectories() throws IOException {
        Path zip = LogSourceFixture.zip(directory, "gc.zip", "gc.log.0", "gc.log.1");

        assertEquals(List.of("gc.log.0", "gc.log.1"), LogFileSources.zipEntryNames(zip));
    }

    @Test
    void reportsTheSizeOfASource() throws IOException {
        Path path = LogSourceFixture.plainText(directory, "gc.log");

        assertEquals(LogSourceFixture.CONTENT.getBytes(StandardCharsets.UTF_8).length,
                LogFileSources.sizeInBytes(path));
    }

    @Test
    void reportsTheUncompressedSizeOfAZipEntry() throws IOException {
        Path zip = LogSourceFixture.zip(directory, "gc.zip", "gc.log.0");

        assertEquals(LogSourceFixture.contentOf("gc.log.0").getBytes(StandardCharsets.UTF_8).length,
                LogFileSources.sizeInBytes(zip, "gc.log.0"));
    }

    @Test
    void reportsAnUnknownSizeForAnEntryThatIsNotInTheZipSource() throws IOException {
        Path zip = LogSourceFixture.zip(directory, "gc.zip", "gc.log.0");

        assertEquals(-1L, LogFileSources.sizeInBytes(zip, "absent.log"));
    }

    @Test
    void failsOnASourceThatIsNotThere() {
        assertThrows(IOException.class, () -> LogFileSources.sizeInBytes(directory.resolve("missing.log")));
        assertThrows(IOException.class, () -> LogFileSources.filesIn(directory.resolve("missing")));
    }

    private List<String> namesOf(List<Path> paths) {
        return paths.stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
    }
}
