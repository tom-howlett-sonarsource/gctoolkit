// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceFormatTest {

    @Test
    void detectsDirectory() throws IOException {
        Path directory = Files.createTempDirectory("gclogsource-format");

        assertEquals(GCLogSourceFormat.DIRECTORY, GCLogSourceFormat.from(directory));
    }

    @Test
    void detectsPlaintext() throws IOException {
        Path file = Files.createTempFile("gclogsource-format", ".log");
        Files.writeString(file, "[0.001s][info][gc] Using G1");

        assertEquals(GCLogSourceFormat.PLAINTEXT, GCLogSourceFormat.from(file));
    }

    @Test
    void detectsGzip() throws IOException {
        Path file = Files.createTempFile("gclogsource-format", ".gz");
        try (GZIPOutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(file))) {
            outputStream.write("line".getBytes());
        }

        assertEquals(GCLogSourceFormat.GZIP, GCLogSourceFormat.from(file));
    }

    @Test
    void detectsZip() throws IOException {
        Path file = Files.createTempFile("gclogsource-format", ".zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(file))) {
            outputStream.putNextEntry(new ZipEntry("gc.log"));
            outputStream.write("line".getBytes());
            outputStream.closeEntry();
        }

        assertEquals(GCLogSourceFormat.ZIP, GCLogSourceFormat.from(file));
    }
}
