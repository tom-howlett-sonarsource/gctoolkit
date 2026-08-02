// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final String LOG_CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversFormatOfEachKindOfSource() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discoverFormat(plainText()));
        assertEquals(LogFileFormat.GZIP, GCLogSource.discoverFormat(gzip()));
        assertEquals(LogFileFormat.ZIP, GCLogSource.discoverFormat(zip()));
        assertEquals(LogFileFormat.DIRECTORY, GCLogSource.discoverFormat(directory));
        assertEquals(LogFileFormat.UNKNOWN, GCLogSource.discoverFormat(null));
    }

    @Test
    void reportsSizeInBytes() throws IOException {
        Path log = plainText();
        assertEquals(LOG_CONTENT.getBytes(StandardCharsets.UTF_8).length, GCLogSource.sizeInBytes(log));
        assertEquals(0L, GCLogSource.sizeInBytes(directory.resolve("does-not-exist.log")));
        assertEquals(0L, GCLogSource.sizeInBytes(null));
    }

    @Test
    void sizeOfDirectoryIsSumOfContainedFiles() throws IOException {
        Path subdirectory = Files.createDirectory(directory.resolve("segments"));
        Files.writeString(subdirectory.resolve("gc.log.0"), "abc", StandardCharsets.UTF_8);
        Files.writeString(subdirectory.resolve("gc.log.1"), "de", StandardCharsets.UTF_8);
        assertEquals(5L, GCLogSource.sizeInBytes(subdirectory));
    }

    @Test
    void streamsPlainZipAndGzipSources() throws IOException {
        assertContainsLogLine(plainText());
        assertContainsLogLine(zip());
        assertContainsLogLine(gzip());
    }

    @Test
    void streamOfUnreadableFormatThrows() {
        assertThrows(IOException.class, () -> GCLogSource.stream(directory, LogFileFormat.DIRECTORY));
        assertThrows(IOException.class, () -> GCLogSource.stream(directory, LogFileFormat.UNKNOWN));
        assertThrows(IOException.class, () -> GCLogSource.stream(directory, null));
    }

    private void assertContainsLogLine(Path path) throws IOException {
        try (Stream<String> lines = GCLogSource.stream(path, GCLogSource.discoverFormat(path))) {
            List<String> collected = lines.map(String::trim).collect(Collectors.toList());
            assertTrue(collected.contains("[0.001s][info][gc] test"));
        }
    }

    private Path plainText() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}
