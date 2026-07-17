// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void opensPlainTextAndReportsStoredBytes() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("gc.log");
        Files.writeString(sourcePath, "first\nsecond\n", StandardCharsets.UTF_8);

        LogFileSource source = LogFileSource.from(sourcePath);

        assertTrue(source.isPlainText());
        assertFalse(source.isZip());
        assertFalse(source.isGZip());
        assertEquals(Files.size(sourcePath), source.sizeInBytes());
        try (Stream<String> lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void opensFirstFileInZipAfterDirectories() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(sourcePath))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("logs/gc.log"));
            zip.write("zip line\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        LogFileSource source = LogFileSource.from(sourcePath);

        assertTrue(source.isZip());
        assertEquals(Files.size(sourcePath), source.sizeInBytes());
        try (Stream<String> lines = source.lines()) {
            assertEquals(List.of("zip line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void opensGZipContent() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("gc.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(sourcePath))) {
            output.write("gzip line\n".getBytes(StandardCharsets.UTF_8));
        }

        LogFileSource source = LogFileSource.from(sourcePath);

        assertTrue(source.isGZip());
        assertEquals(Files.size(sourcePath), source.sizeInBytes());
        try (Stream<String> lines = source.lines()) {
            assertEquals(List.of("gzip line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversDirectoryEntriesAndMatchingSiblings() throws IOException {
        Path current = Files.writeString(temporaryDirectory.resolve("gc.log"), "current");
        Path rotated = Files.writeString(temporaryDirectory.resolve("gc.log.0"), "rotated");
        Files.writeString(temporaryDirectory.resolve("other.log"), "other");

        assertEquals(
                Set.of("gc.log", "gc.log.0", "other.log"),
                fileNames(LogFileSources.discover(temporaryDirectory, "ignored")));
        assertEquals(
                Set.of("gc.log", "gc.log.0"),
                fileNames(LogFileSources.discover(current, "gc.log")));
        assertTrue(LogFileSources.discover(current, "gc.log").contains(rotated));
    }

    @Test
    void defersMissingSourceFailureUntilContentIsOpened() throws IOException {
        LogFileSource source = LogFileSource.from(temporaryDirectory.resolve("missing.log"));

        assertTrue(source.isPlainText());
        assertThrows(IOException.class, source::lines);
        assertThrows(IOException.class, source::sizeInBytes);
    }

    @Test
    void identifiesDirectoriesAsNonReadableSources() throws IOException {
        LogFileSource source = LogFileSource.from(temporaryDirectory);

        assertEquals(temporaryDirectory, source.path());
        assertTrue(source.isDirectory());
        assertThrows(IOException.class, source::lines);
    }

    private Set<String> fileNames(List<Path> paths) {
        return paths.stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toSet());
    }
}
