// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final byte[] FIRST_LOG = "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_LOG = "other entry\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversAndSizesPlainSource() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.write(path, FIRST_LOG);

        GCLogSource source = GCLogSource.from(path);

        assertEquals(GCLogSource.Type.PLAIN_TEXT, source.getType());
        assertEquals(path, source.getPath());
        assertEquals(FIRST_LOG.length, source.byteSize());
        assertEquals(FIRST_LOG.length, source.contentByteSize());
        assertEquals(List.of("first line", "second line"), read(source));
    }

    @Test
    void opensAndSizesGzipContent() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(FIRST_LOG);
        }

        GCLogSource source = GCLogSource.from(path);

        assertEquals(GCLogSource.Type.GZIP, source.getType());
        assertEquals(Files.size(path), source.byteSize());
        assertEquals(FIRST_LOG.length, source.contentByteSize());
        assertEquals(List.of("first line", "second line"), read(source));
    }

    @Test
    void discoversAndOpensZipEntries() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/first.log", FIRST_LOG);
            writeEntry(output, "logs/second.log", SECOND_LOG);
        }

        GCLogSource source = GCLogSource.from(path);

        assertEquals(GCLogSource.Type.ZIP, source.getType());
        assertEquals(List.of("logs/first.log", "logs/second.log"), source.entries());
        assertEquals(FIRST_LOG.length, source.contentByteSize());
        assertEquals(List.of("first line", "second line"), read(source));
        try (var lines = source.lines("logs/second.log")) {
            assertEquals(List.of("other entry"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversDirectoryChildren() throws IOException {
        Path first = Files.write(directory.resolve("first.log"), FIRST_LOG);
        Path second = Files.write(directory.resolve("second.log"), SECOND_LOG);

        GCLogSource source = GCLogSource.from(directory);

        assertEquals(GCLogSource.Type.DIRECTORY, source.getType());
        assertEquals(FIRST_LOG.length + SECOND_LOG.length, source.contentByteSize());
        assertTrue(GCLogSource.discover(directory).containsAll(List.of(first, second)));
    }

    private static List<String> read(GCLogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}
