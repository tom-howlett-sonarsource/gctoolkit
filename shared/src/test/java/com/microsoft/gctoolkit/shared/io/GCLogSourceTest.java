// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final String FIRST_LINE = "[0.001s][info][gc] first";
    private static final String SECOND_LINE = "[0.002s][info][gc] second";
    private static final String CONTENT = FIRST_LINE + "\n" + SECOND_LINE + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversSourceFormatsAndByteSize() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip));
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
        assertEquals(Files.size(plain), GCLogSource.byteSize(plain));
    }

    @Test
    void opensPlainGzipAndFirstFileInZip() throws IOException {
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), read(GCLogSource.open(writePlain())));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), read(GCLogSource.open(writeGzip())));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), read(GCLogSource.open(writeZip())));
    }

    @Test
    void listsAndOpensNamedZipEntries() throws IOException {
        Path zip = writeZip();

        assertEquals(List.of("logs/gc.log"), GCLogSource.zipEntries(zip));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), read(GCLogSource.openZipEntry(zip, "logs/gc.log")));
    }

    private List<String> read(java.util.stream.Stream<String> lines) {
        try (java.util.stream.Stream<String> closeableLines = lines) {
            return closeableLines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path source = directory.resolve("gc.log");
        Files.writeString(source, CONTENT, StandardCharsets.UTF_8);
        return source;
    }

    private Path writeGzip() throws IOException {
        Path source = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path writeZip() throws IOException {
        Path source = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }
}
