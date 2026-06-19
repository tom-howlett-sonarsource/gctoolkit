// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceMetadataTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsPlainTextFiles() throws IOException {
        Path logFile = temporaryDirectory.resolve("gc.log");
        Files.writeString(logFile, "plain text");

        LogSourceMetadata metadata = new LogSourceMetadata(logFile);

        assertTrue(metadata.isPlainText());
    }

    @Test
    void detectsDirectories() throws IOException {
        LogSourceMetadata metadata = new LogSourceMetadata(temporaryDirectory);

        assertTrue(metadata.isDirectory());
    }

    @Test
    void detectsZipFiles() throws IOException {
        Path zipFile = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zipOutputStream.putNextEntry(new ZipEntry("gc.log"));
            zipOutputStream.write("first".getBytes());
            zipOutputStream.closeEntry();
        }

        LogSourceMetadata metadata = new LogSourceMetadata(zipFile);

        assertTrue(metadata.isZip());
    }

    @Test
    void detectsGZipFiles() throws IOException {
        Path gzipFile = temporaryDirectory.resolve("gc.log.gz");
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(gzipFile))) {
            gzipOutputStream.write("first".getBytes());
        }

        LogSourceMetadata metadata = new LogSourceMetadata(gzipFile);

        assertTrue(metadata.isGZip());
    }
}
