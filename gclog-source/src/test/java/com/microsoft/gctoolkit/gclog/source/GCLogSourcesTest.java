// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.source;

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

class GCLogSourcesTest {

    private static final byte[] FIRST_LOG = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_LOG = "third\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainSourceAndReportsByteSize() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        Files.write(path, FIRST_LOG);

        List<GCLogSource> sources = GCLogSources.discover(path);

        assertEquals(GCLogSources.Format.PLAIN_TEXT, GCLogSources.format(path));
        assertEquals(1, sources.size());
        assertEquals(path, sources.get(0).path());
        assertEquals("gc.log", sources.get(0).name());
        assertEquals(FIRST_LOG.length, sources.get(0).byteSize());
        assertEquals(List.of("first", "second"), lines(sources.get(0)));
    }

    @Test
    void discoversGzipByContentAndOpensDecompressedStream() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(FIRST_LOG);
        }

        GCLogSource source = GCLogSources.first(path);

        assertEquals(GCLogSources.Format.GZIP, GCLogSources.format(path));
        assertEquals(FIRST_LOG.length, source.byteSize());
        assertEquals(List.of("first", "second"), lines(source));
    }

    @Test
    void discoversZipEntriesAndOpensEachEntry() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/gc.log.1", FIRST_LOG);
            writeEntry(output, "logs/gc.log.2", SECOND_LOG);
        }

        List<GCLogSource> sources = GCLogSources.discover(path);

        assertEquals(GCLogSources.Format.ZIP, GCLogSources.format(path));
        assertEquals(List.of("logs/gc.log.1", "logs/gc.log.2"),
                sources.stream().map(GCLogSource::name).collect(Collectors.toList()));
        assertEquals(FIRST_LOG.length, sources.get(0).byteSize());
        assertEquals(List.of("third"), lines(sources.get(1)));
        assertEquals("logs/gc.log.2", GCLogSources.find(path, "logs/gc.log.2").name());
    }

    @Test
    void discoversFilesInDirectory() throws IOException {
        Files.write(temporaryDirectory.resolve("gc.log.0"), FIRST_LOG);
        Files.write(temporaryDirectory.resolve("gc.log.1"), SECOND_LOG);
        Files.createDirectory(temporaryDirectory.resolve("ignored"));

        List<GCLogSource> sources = GCLogSources.discover(temporaryDirectory);

        assertEquals(GCLogSources.Format.DIRECTORY, GCLogSources.format(temporaryDirectory));
        assertEquals(2, sources.size());
    }

    @Test
    void rejectsMissingZipEntry() throws IOException {
        Path path = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            writeEntry(output, "gc.log", FIRST_LOG);
        }

        assertThrows(IOException.class, () -> GCLogSources.find(path, "missing.log"));
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }

    private static List<String> lines(GCLogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }
}
