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

import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writePlainText;
import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writeZip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceFilesTest {

    @TempDir
    Path directory;

    @Test
    void listsTheFilesInADirectory() throws IOException {
        writePlainText(directory, "gc.log", "a line");
        writePlainText(directory, "gc.log.1", "another line");

        assertEquals(List.of("gc.log", "gc.log.1"), namesOf(LogSourceFiles.filesIn(directory)));
    }

    @Test
    void listingAnUnknownDirectoryFails() {
        assertThrows(IOException.class, () -> LogSourceFiles.filesIn(directory.resolve("not-a-directory")));
    }

    @Test
    void findsTheSiblingsOfARotatingSegment() throws IOException {
        Path current = writePlainText(directory, "gc.log", "a line");
        writePlainText(directory, "gc.log.1", "another line");
        writePlainText(directory, "other.log", "not a segment");

        assertEquals(List.of("gc.log", "gc.log.1"), namesOf(LogSourceFiles.siblingsStartingWith(current, "gc.log")));
    }

    @Test
    void namesTheEntriesInAZipArchiveSkippingDirectories() throws IOException {
        Path archive = writeZip(directory, "gc.zip", true, "logs/gc.log", "a line");

        assertEquals(List.of("logs/gc.log"), LogSourceFiles.zipEntryNames(archive));
    }

    @Test
    void namingTheEntriesOfSomethingThatIsNotAZipArchiveFails() throws IOException {
        Path notAnArchive = writePlainText(directory, "gc.log", "a line");

        assertThrows(IOException.class, () -> LogSourceFiles.zipEntryNames(notAnArchive));
    }

    @Test
    void sizesAFileInBytes() throws IOException {
        Path log = writePlainText(directory, "gc.log", "0123456789");

        assertEquals(11L, LogSourceFiles.sizeInBytes(log));
    }

    @Test
    void sizesADirectoryAsTheSumOfTheFilesItHolds() throws IOException {
        writePlainText(directory, "gc.log", "0123456789");
        writePlainText(directory, "gc.log.1", "0123");
        Files.createDirectory(directory.resolve("nested"));
        writePlainText(directory.resolve("nested"), "gc.log.2", "not counted");

        assertEquals(16L, LogSourceFiles.sizeInBytes(directory));
    }

    @Test
    void sizingAnUnknownSourceFails() {
        assertThrows(IOException.class, () -> LogSourceFiles.sizeInBytes(directory.resolve("does-not-exist.log")));
    }

    @Test
    void anEmptyDirectoryHoldsNoBytes() throws IOException {
        assertEquals(0L, LogSourceFiles.sizeInBytes(directory));
        assertTrue(LogSourceFiles.filesIn(directory).isEmpty());
    }

    private List<String> namesOf(List<Path> paths) {
        return paths.stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
    }
}
