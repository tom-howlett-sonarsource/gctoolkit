// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    private static final byte[] FIRST_LOG = "first log\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_LOG = "second log\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainAndGzipFilesInDirectory() throws IOException {
        Path plain = temporaryDirectory.resolve("gc.log");
        Path gzip = temporaryDirectory.resolve("gc.log.gz");
        Files.write(plain, FIRST_LOG);
        writeGzip(gzip, SECOND_LOG);

        Map<String, GCLogSource> sources = GCLogSource.discover(temporaryDirectory).stream()
                .collect(Collectors.toMap(GCLogSource::name, Function.identity()));

        assertEquals(List.of("gc.log", "gc.log.gz"), sources.keySet().stream().sorted().collect(Collectors.toList()));
        assertEquals(GCLogSource.Format.PLAIN_TEXT, sources.get("gc.log").format());
        assertEquals(GCLogSource.Format.GZIP, sources.get("gc.log.gz").format());
    }

    @Test
    void discoversEachFileEntryInZipOrder() throws IOException {
        Path zip = temporaryDirectory.resolve("rotating.zip");
        writeZip(zip);

        List<GCLogSource> sources = GCLogSource.discover(zip);

        assertEquals(List.of("logs/gc.log.0", "logs/gc.log.1"),
                sources.stream().map(GCLogSource::name).collect(Collectors.toList()));
        assertEquals(GCLogSource.Format.ZIP, sources.get(0).format());
    }

    @Test
    void opensAndSizesPlainGzipAndZipSources() throws IOException {
        Path plain = temporaryDirectory.resolve("gc.log");
        Path gzip = temporaryDirectory.resolve("gc.log.gz");
        Path zip = temporaryDirectory.resolve("gc.log.zip");
        Files.write(plain, FIRST_LOG);
        writeGzip(gzip, FIRST_LOG);
        writeZip(zip);

        assertContentAndSize(GCLogSource.first(plain), FIRST_LOG);
        assertContentAndSize(GCLogSource.first(gzip), FIRST_LOG);
        assertContentAndSize(GCLogSource.first(zip), FIRST_LOG);
    }

    @Test
    void rejectsSourcesWithoutReadableLogContent() throws IOException {
        Path emptyDirectory = Files.createDirectory(temporaryDirectory.resolve("empty"));

        IOException exception = assertThrows(IOException.class, () -> GCLogSource.first(emptyDirectory));

        assertEquals("No log source found in " + emptyDirectory, exception.getMessage());
    }

    @Test
    void readsLinesFromNamedZipEntry() throws IOException {
        Path zip = temporaryDirectory.resolve("rotating.zip");
        writeZip(zip);
        GCLogSource source = GCLogSource.zipEntry(zip, "logs/gc.log.1");

        List<String> lines;
        try (Stream<String> stream = source.lines()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("second log"), lines);
        assertEquals(zip, source.path());
        assertEquals(SECOND_LOG.length, source.size());
        assertEquals(SECOND_LOG.length, source.size());
    }

    @Test
    void rejectsMissingZipEntry() throws IOException {
        Path zip = temporaryDirectory.resolve("rotating.zip");
        writeZip(zip);

        IOException exception = assertThrows(IOException.class,
                () -> GCLogSource.zipEntry(zip, "missing.log"));

        assertEquals("ZIP entry not found: missing.log", exception.getMessage());
    }

    @Test
    void reportsEntryRemovedAfterDiscovery() throws IOException {
        Path zip = temporaryDirectory.resolve("rotating.zip");
        writeZip(zip);
        GCLogSource source = GCLogSource.first(zip);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeZipEntry(output, "replacement.log", SECOND_LOG);
        }

        IOException exception = assertThrows(IOException.class, source::open);

        assertEquals("ZIP entry not found: logs/gc.log.0", exception.getMessage());
    }

    @Test
    void reportsMissingPhysicalPath() {
        Path missing = temporaryDirectory.resolve("missing.log");

        assertThrows(IOException.class, () -> GCLogSource.formatOf(missing));
    }

    private static void assertContentAndSize(GCLogSource source, byte[] expected) throws IOException {
        try (InputStream input = source.open()) {
            assertArrayEquals(expected, input.readAllBytes());
        }
        assertEquals(expected.length, source.size());
    }

    private static void writeGzip(Path path, byte[] content) throws IOException {
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content);
        }
    }

    private static void writeZip(Path path) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeZipEntry(output, "logs/gc.log.0", FIRST_LOG);
            writeZipEntry(output, "logs/gc.log.1", SECOND_LOG);
        }
    }

    private static void writeZipEntry(ZipOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}
