// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    @Test
    void detectsDirectory(@TempDir Path tmp) {
        assertEquals(SourceFormat.DIRECTORY, GCLogSource.detect(tmp));
    }

    @Test
    void detectsPlainText(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve("plain.log");
        Files.writeString(plain, "hello\nworld\n");
        assertEquals(SourceFormat.PLAINTEXT, GCLogSource.detect(plain));
        try (Stream<String> stream = GCLogSource.openPlain(plain)) {
            assertEquals(List.of("hello", "world"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void detectsGZip(@TempDir Path tmp) throws IOException {
        Path gz = tmp.resolve("plain.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("alpha\nbeta\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(SourceFormat.GZIP, GCLogSource.detect(gz));
        try (Stream<String> stream = GCLogSource.openGZip(gz)) {
            assertEquals(List.of("alpha", "beta"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void detectsZip(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("plain.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("plain.log"));
            out.write("gamma\ndelta\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertEquals(SourceFormat.ZIP, GCLogSource.detect(zip));
        try (Stream<String> stream = GCLogSource.openZip(zip)) {
            assertEquals(List.of("gamma", "delta"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSkipsLeadingDirectoryEntries(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("with-dir.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("dir/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("dir/inner.log"));
            out.write("one\ntwo\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> stream = GCLogSource.openZip(zip)) {
            assertEquals(List.of("one", "two"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void hasMagicIsFalseForShortFile(@TempDir Path tmp) throws IOException {
        Path empty = tmp.resolve("empty.log");
        Files.createFile(empty);
        assertFalse(GCLogSource.hasMagic(empty, 0x50, 0x4b));
    }

    @Test
    void hasMagicMatchesFirstTwoBytes(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("bytes.bin");
        Files.write(file, new byte[]{(byte) 0x1F, (byte) 0x8B, (byte) 0x00, (byte) 0x00});
        assertTrue(GCLogSource.hasMagic(file, GCLogSource.GZIP_MAGIC1, GCLogSource.GZIP_MAGIC2));
        assertFalse(GCLogSource.hasMagic(file, GCLogSource.ZIP_MAGIC1, GCLogSource.ZIP_MAGIC2));
    }
}
