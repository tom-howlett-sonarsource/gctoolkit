// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourcesTest {

    private static final String GC_LOG = "gc.log";

    @TempDir
    private Path tempDir;

    @Test
    void listsRegularFilesInDirectory() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("discovery"));
        Path file = Files.createFile(directory.resolve(GC_LOG));
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(List.of(file), GCLogSources.filesIn(directory));
    }

    @Test
    void listsSiblingFilesByRootPattern() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("siblings"));
        Path first = Files.createFile(directory.resolve(GC_LOG));
        Path second = Files.createFile(directory.resolve("gc.log.0"));
        Files.createFile(directory.resolve("other.log"));

        assertEquals(List.of(first, second), GCLogSources.siblingFilesStartingWith(first, GC_LOG));
    }

    @Test
    void listsZipFileEntries() throws IOException {
        Path file = tempDir.resolve("discovery.zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(file))) {
            outputStream.putNextEntry(new ZipEntry("directory/"));
            outputStream.closeEntry();
            writeEntry(outputStream, GC_LOG, "line");
            writeEntry(outputStream, "__MACOSX/._gc.log", "metadata");
            writeEntry(outputStream, "__MACOSX/directory/._gc.log.0", "metadata");
        }

        assertEquals(List.of(GC_LOG), GCLogSources.zipEntryNames(file));
    }

    private static void writeEntry(ZipOutputStream outputStream, String name, String content) throws IOException {
        outputStream.putNextEntry(new ZipEntry(name));
        outputStream.write(content.getBytes());
        outputStream.closeEntry();
    }
}
