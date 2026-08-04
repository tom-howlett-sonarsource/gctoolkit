// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversSupportedSourceFormats() throws IOException {
        assertEquals(GCLogSource.Format.PLAIN_TEXT, new GCLogSource(writePlain()).format());
        assertEquals(GCLogSource.Format.GZIP, new GCLogSource(writeGzip()).format());
        assertEquals(GCLogSource.Format.ZIP, new GCLogSource(writeZip()).format());
        assertEquals(GCLogSource.Format.DIRECTORY, new GCLogSource(directory).format());
    }

    @Test
    void reportsUncompressedContentSize() throws IOException {
        long expected = CONTENT.getBytes(StandardCharsets.UTF_8).length;

        assertEquals(expected, new GCLogSource(writePlain()).size());
        assertEquals(expected, new GCLogSource(writeGzip()).size());
        assertEquals(expected, new GCLogSource(writeZip()).size());
    }

    @Test
    void opensLinesFromPlainGzipAndFirstZipFile() throws IOException {
        List<String> expected = List.of("first line", "second line");

        assertEquals(expected, readLines(writePlain()));
        assertEquals(expected, readLines(writeGzip()));
        assertEquals(expected, readLines(writeZip()));
    }

    @Test
    void rejectsOpeningAndSizingDirectories() {
        GCLogSource source = new GCLogSource(directory);

        assertThrows(IOException.class, source::open);
        assertThrows(IOException.class, source::size);
    }

    @Test
    void requiresBothMagicBytesAndRejectsMalformedZip() throws IOException {
        Path partialGzipMagic = Files.write(directory.resolve("partial-gzip.log"),
                new byte[]{0x1f, 0x00});
        Path partialZipMagic = Files.write(directory.resolve("partial-zip.log"),
                new byte[]{0x50, 0x00});
        byte[] malformedZipBytes = Files.readAllBytes(writeZip());
        malformedZipBytes[8] = 99;
        Path malformedZip = Files.write(directory.resolve("malformed.zip"), malformedZipBytes);

        assertEquals(GCLogSource.Format.PLAIN_TEXT, new GCLogSource(partialGzipMagic).format());
        assertEquals(GCLogSource.Format.PLAIN_TEXT, new GCLogSource(partialZipMagic).format());
        assertThrows(IOException.class, new GCLogSource(malformedZip)::open);
        assertThrows(NullPointerException.class, () -> new GCLogSource(null));
    }

    private List<String> readLines(Path path) throws IOException {
        try (var lines = new GCLogSource(path).lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        return Files.writeString(directory.resolve("gc.log"), CONTENT, StandardCharsets.UTF_8);
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}
