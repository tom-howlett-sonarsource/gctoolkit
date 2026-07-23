// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileFormatTest {

    @TempDir
    Path tempDir;

    @Test
    void detectPlainText() throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.writeString(file, "some plain text gc log content\n");
        assertEquals(FileFormat.PLAINTEXT, FileFormat.detect(file));
    }

    @Test
    void detectDirectory() {
        assertEquals(FileFormat.DIRECTORY, FileFormat.detect(tempDir));
    }

    @Test
    void detectZip() throws IOException {
        Path file = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write("zip content\n".getBytes());
            zos.closeEntry();
        }
        assertEquals(FileFormat.ZIP, FileFormat.detect(file));
    }

    @Test
    void detectGzip() throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
            gos.write("gzip content\n".getBytes());
        }
        assertEquals(FileFormat.GZIP, FileFormat.detect(file));
    }

    @Test
    void detectEmptyFileAsPlainText() throws IOException {
        Path file = tempDir.resolve("empty.log");
        Files.createFile(file);
        assertEquals(FileFormat.PLAINTEXT, FileFormat.detect(file));
    }
}
