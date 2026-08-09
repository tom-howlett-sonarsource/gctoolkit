// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final String LINE = "[0.001s][info][gc] hello";
    private static final String CONTENT = LINE + "\n";

    @TempDir
    Path directory;

    @Test
    void detectsPlaintext() throws IOException {
        Path plain = writePlain("plain.log");
        assertEquals(LogFormat.PLAINTEXT, GCLogSources.detectFormat(plain));
    }

    @Test
    void detectsGZip() throws IOException {
        Path gzip = writeGzip("plain.log.gz");
        assertEquals(LogFormat.GZIP, GCLogSources.detectFormat(gzip));
    }

    @Test
    void detectsZip() throws IOException {
        Path zip = writeZip("plain.log.zip", "entry.log");
        assertEquals(LogFormat.ZIP, GCLogSources.detectFormat(zip));
    }

    @Test
    void detectsDirectory() {
        assertEquals(LogFormat.DIRECTORY, GCLogSources.detectFormat(directory));
    }

    @Test
    void detectFormatReturnsUnknownForMissingPath() {
        assertEquals(LogFormat.UNKNOWN, GCLogSources.detectFormat(directory.resolve("nope.log")));
    }

    @Test
    void readsPlaintextMagicBytes() throws IOException {
        Path plain = writePlain("plain.log");
        int[] magic = GCLogSources.readMagicBytes(plain);
        assertEquals(2, magic.length);
        assertEquals((int) '[', magic[0]);
    }

    @Test
    void magicBytesTrimmedForShortFile() throws IOException {
        Path oneByte = directory.resolve("one-byte");
        Files.write(oneByte, new byte[] { 0x41 });
        assertArrayEquals(new int[] { 0x41 }, GCLogSources.readMagicBytes(oneByte));
    }

    @Test
    void opensPlainStream() throws IOException {
        Path plain = writePlain("plain.log");
        try (Stream<String> stream = GCLogSources.openPlainStream(plain)) {
            assertTrue(stream.collect(Collectors.toList()).contains(LINE));
        }
    }

    @Test
    void opensGZipStream() throws IOException {
        Path gzip = writeGzip("plain.log.gz");
        try (Stream<String> stream = GCLogSources.openGZipStream(gzip)) {
            assertTrue(stream.collect(Collectors.toList()).contains(LINE));
        }
    }

    @Test
    void opensZipStreamFirstEntry() throws IOException {
        Path zip = writeZip("plain.log.zip", "entry.log");
        try (Stream<String> stream = GCLogSources.openZipStream(zip)) {
            List<String> collected = stream.collect(Collectors.toList());
            assertTrue(collected.contains(LINE));
        }
    }

    @Test
    void openStreamDispatchesOnFormat() throws IOException {
        Path plain = writePlain("plain.log");
        try (Stream<String> stream = GCLogSources.openStream(plain)) {
            assertTrue(stream.collect(Collectors.toList()).contains(LINE));
        }
    }

    @Test
    void openStreamRejectsUnknownFormat() {
        Path missing = directory.resolve("missing.log");
        assertThrows(IOException.class, () -> GCLogSources.openStream(missing, LogFormat.UNKNOWN));
    }

    private Path writePlain(String name) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip(String name) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String zipName, String entryName) throws IOException {
        Path path = directory.resolve(zipName);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(path))) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return path;
    }
}
