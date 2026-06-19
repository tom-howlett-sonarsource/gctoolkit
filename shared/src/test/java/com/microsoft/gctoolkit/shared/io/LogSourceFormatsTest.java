// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceFormatsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void detectsSupportedFormats() throws IOException {
        Path plainText = tempDirectory.resolve("gc.log");
        Files.write(plainText, List.of("line"));

        Path gzip = tempDirectory.resolve("gc.log.gz");
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            gzipOutputStream.write("line\n".getBytes());
        }

        Path zip = tempDirectory.resolve("gc.log.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zip))) {
            zipOutputStream.putNextEntry(new ZipEntry("gc.log"));
            zipOutputStream.write("line\n".getBytes());
            zipOutputStream.closeEntry();
        }

        assertEquals(LogSourceFormat.DIRECTORY, LogSourceFormats.detect(tempDirectory));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceFormats.detect(plainText));
        assertEquals(LogSourceFormat.GZIP, LogSourceFormats.detect(gzip));
        assertEquals(LogSourceFormat.ZIP, LogSourceFormats.detect(zip));
    }
}
