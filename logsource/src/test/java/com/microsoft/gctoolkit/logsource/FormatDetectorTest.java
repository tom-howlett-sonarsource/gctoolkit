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

class FormatDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectPlainText() throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.write(file, "GC log line 1\nGC log line 2\n".getBytes());

        assertEquals(FileFormat.PLAINTEXT, FormatDetector.detect(file));
    }

    @Test
    void detectGzip() throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
            out.write("GC log line 1\nGC log line 2\n".getBytes());
        }

        assertEquals(FileFormat.GZIP, FormatDetector.detect(file));
    }

    @Test
    void detectZip() throws IOException {
        Path file = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write("GC log line 1\nGC log line 2\n".getBytes());
            out.closeEntry();
        }

        assertEquals(FileFormat.ZIP, FormatDetector.detect(file));
    }

    @Test
    void detectDirectory() {
        assertEquals(FileFormat.DIRECTORY, FormatDetector.detect(tempDir));
    }
}
