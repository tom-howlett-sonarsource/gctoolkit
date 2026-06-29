// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourcesTest {

    @Test
    void listsRegularFilesInDirectory() throws IOException {
        Path directory = Files.createTempDirectory("gclogsource-discovery");
        Path file = Files.createFile(directory.resolve("gc.log"));
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(List.of(file), GCLogSources.filesIn(directory));
    }

    @Test
    void listsSiblingFilesByRootPattern() throws IOException {
        Path directory = Files.createTempDirectory("gclogsource-discovery");
        Path first = Files.createFile(directory.resolve("gc.log"));
        Path second = Files.createFile(directory.resolve("gc.log.0"));
        Files.createFile(directory.resolve("other.log"));

        assertEquals(List.of(first, second), GCLogSources.siblingFilesStartingWith(first, "gc.log"));
    }

    @Test
    void listsZipFileEntries() throws IOException {
        Path file = Files.createTempFile("gclogsource-discovery", ".zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(file))) {
            outputStream.putNextEntry(new ZipEntry("directory/"));
            outputStream.closeEntry();
            outputStream.putNextEntry(new ZipEntry("gc.log"));
            outputStream.write("line".getBytes());
            outputStream.closeEntry();
        }

        assertEquals(List.of("gc.log"), GCLogSources.zipEntryNames(file));
    }
}
