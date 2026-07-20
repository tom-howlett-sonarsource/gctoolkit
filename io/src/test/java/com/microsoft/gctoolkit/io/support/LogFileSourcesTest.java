// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileSourcesTest {

    @Test
    void listsDirectoryContents(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("a.log"), "a");
        Files.writeString(tmp.resolve("b.log"), "b");
        List<String> names = LogFileSources.listDirectory(tmp).stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of("a.log", "b.log"), names);
    }

    @Test
    void listsDirectoryStartingWithPrefix(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("gc.log"), "");
        Files.writeString(tmp.resolve("gc.log.1"), "");
        Files.writeString(tmp.resolve("other.txt"), "");
        List<String> names = LogFileSources.listDirectoryStartingWith(tmp, "gc.log").stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of("gc.log", "gc.log.1"), names);
    }

    @Test
    void listsZipEntryNamesSkippingDirectories(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("rot.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("nested/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log.0"));
            out.write("0".getBytes());
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log.1"));
            out.write("1".getBytes());
            out.closeEntry();
        }
        List<String> names = LogFileSources.listZipEntryNames(zip);
        names.sort(Comparator.naturalOrder());
        assertEquals(List.of("gc.log.0", "gc.log.1"), names);
    }

    @Test
    void listZipEntryNamesFailsForMissingArchive(@TempDir Path tmp) {
        assertThrows(IOException.class, () -> LogFileSources.listZipEntryNames(tmp.resolve("missing.zip")));
    }

    @Test
    void listDirectoryStartingWithReturnsEmptyWhenNoMatch(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("only.txt"), "");
        assertTrue(LogFileSources.listDirectoryStartingWith(tmp, "gc").isEmpty());
    }

    @Test
    void rejectsNullArguments(@TempDir Path tmp) {
        assertThrows(NullPointerException.class, () -> LogFileSources.listDirectory(null));
        assertThrows(NullPointerException.class, () -> LogFileSources.listDirectoryStartingWith(null, "x"));
        assertThrows(NullPointerException.class, () -> LogFileSources.listDirectoryStartingWith(tmp, null));
        assertThrows(NullPointerException.class, () -> LogFileSources.listZipEntryNames(null));
    }
}
