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

class FileFormatDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectPlainText() throws IOException {
        Path plain = tempDir.resolve("gc.log");
        Files.writeString(plain, "Some GC log content\n");
        assertEquals(FileFormat.PLAINTEXT, FileFormatDetector.detect(plain));
    }

    @Test
    void detectDirectory() {
        assertEquals(FileFormat.DIRECTORY, FileFormatDetector.detect(tempDir));
    }

    @Test
    void detectZip() throws IOException {
        Path zip = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write("GC log content\n".getBytes());
            zos.closeEntry();
        }
        assertEquals(FileFormat.ZIP, FileFormatDetector.detect(zip));
    }

    @Test
    void detectGZip() throws IOException {
        Path gzip = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzip.toFile()))) {
            gos.write("GC log content\n".getBytes());
        }
        assertEquals(FileFormat.GZIP, FileFormatDetector.detect(gzip));
    }

    @Test
    void detectEmptyFileAsPlainText() throws IOException {
        Path empty = tempDir.resolve("empty.log");
        Files.createFile(empty);
        assertEquals(FileFormat.PLAINTEXT, FileFormatDetector.detect(empty));
    }
}
