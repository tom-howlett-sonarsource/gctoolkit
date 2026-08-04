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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final String FIRST_LINE = "[0.001s][info][gc] first";
    private static final String SECOND_LINE = "[0.002s][info][gc] second";

    @TempDir
    Path directory;

    @Test
    void discoversFormatsFromContentAndReportsSourceBytes() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plain).getFormat());
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip).getFormat());
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip).getFormat());
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory).getFormat());
        assertEquals(Files.size(gzip), GCLogSource.discover(gzip).sizeInBytes());
    }

    @Test
    void opensPlainGzipAndFirstZipFileAsLines() throws IOException {
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), readLines(writePlain()));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), readLines(writeGzip()));
        assertEquals(List.of(FIRST_LINE), readLines(writeZip()));
    }

    @Test
    void listsAndOpensNamedZipEntries() throws IOException {
        GCLogSource source = GCLogSource.discover(writeZip());

        assertEquals(List.of("first.log", "second.log"), source.zipEntries());
        try (var lines = source.lines("second.log")) {
            assertEquals(List.of(SECOND_LINE), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    private List<String> readLines(Path path) throws IOException {
        try (var lines = GCLogSource.discover(path).lines()) {
            return lines.collect(java.util.stream.Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain.data");
        Files.writeString(path, FIRST_LINE + "\n" + SECOND_LINE + "\n", StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gzip.data");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write((FIRST_LINE + "\n" + SECOND_LINE + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("zip.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            writeEntry(output, "first.log", FIRST_LINE);
            writeEntry(output, "second.log", SECOND_LINE);
        }
        return path;
    }

    private void writeEntry(ZipOutputStream output, String name, String line) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
