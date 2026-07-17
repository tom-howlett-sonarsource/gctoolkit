// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainTextByContent() throws IOException {
        Path path = writePlainText("plain.zip", "first\nsecond\n");

        GCLogSource source = GCLogSource.from(path);

        assertTrue(source.isPlainText());
        assertFalse(source.isZip());
        assertFalse(source.isGZip());
    }

    @Test
    void discoversDirectory() throws IOException {
        GCLogSource source = GCLogSource.from(temporaryDirectory);

        assertTrue(source.isDirectory());
        assertThrows(IOException.class, source::lines);
    }

    @Test
    void reportsSourceByteSize() throws IOException {
        Path path = writePlainText("gc.log", "four");

        assertEquals(Files.size(path), GCLogSource.from(path).size());
    }

    @Test
    void opensPlainTextLines() throws IOException {
        GCLogSource source = GCLogSource.from(writePlainText("gc.log", "first\nsecond\n"));

        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void opensFirstZipFileAndSkipsDirectories() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            writeZipEntry(zip, "logs/first.log", "first\nsecond\n");
            writeZipEntry(zip, "logs/ignored.log", "ignored\n");
        }

        GCLogSource source = GCLogSource.from(path);

        assertTrue(source.isZip());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void opensGZipLines() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("first\nsecond\n".getBytes());
        }

        GCLogSource source = GCLogSource.from(path);

        assertTrue(source.isGZip());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void emptyZipProducesNoLines() throws IOException {
        Path path = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(path))) {
        }

        try (var lines = GCLogSource.from(path).lines()) {
            assertEquals(List.of(), lines.collect(toList()));
        }
    }

    private Path writePlainText(String fileName, String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(fileName), content);
    }

    private static void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes());
        zip.closeEntry();
    }
}
