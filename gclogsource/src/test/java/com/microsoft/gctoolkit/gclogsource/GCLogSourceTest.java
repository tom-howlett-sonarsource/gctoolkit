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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainTextAndReportsByteSize() throws IOException {
        Path log = writePlainText("plain.log", "one\ntwo\n");

        GCLogSource source = GCLogSource.from(log);

        assertEquals(log, source.path());
        assertEquals(GCLogSource.Format.PLAIN_TEXT, source.format());
        assertEquals(Files.size(log), source.size());
        assertEquals(List.of("plain.log"), source.entries());
        try (var lines = source.allLines()) {
            assertEquals(List.of("one", "two"), lines.collect(toList()));
        }
    }

    @Test
    void discoversGzipAndOpensLines() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(log))) {
            output.write("one\ntwo\n".getBytes(StandardCharsets.UTF_8));
        }

        GCLogSource source = GCLogSource.from(log);

        assertEquals(GCLogSource.Format.GZIP, source.format());
        try (var lines = source.lines()) {
            assertEquals(List.of("one", "two"), lines.collect(toList()));
        }
    }

    @Test
    void discoversZipEntriesAndOpensRequestedEntry() throws IOException {
        Path log = writeZip();

        GCLogSource source = GCLogSource.from(log);

        assertEquals(GCLogSource.Format.ZIP, source.format());
        assertEquals(List.of("first.log", "second.log"), source.entries());
        try (var lines = source.lines("second.log")) {
            assertEquals(List.of("three", "four"), lines.collect(toList()));
        }
    }

    @Test
    void opensFirstZipEntryByDefault() throws IOException {
        GCLogSource source = GCLogSource.from(writeZip());

        try (var lines = source.lines()) {
            assertEquals(List.of("one", "two"), lines.collect(toList()));
        }
    }

    @Test
    void opensAllZipEntriesInArchiveOrder() throws IOException {
        GCLogSource source = GCLogSource.from(writeZip());

        try (var lines = source.allLines()) {
            assertEquals(List.of("one", "two", "three", "four"), lines.collect(toList()));
        }
    }

    @Test
    void discoversDirectoryEntries() throws IOException {
        writePlainText("first.log", "one\n");
        writePlainText("second.log", "two\n");

        GCLogSource source = GCLogSource.from(temporaryDirectory);

        assertEquals(GCLogSource.Format.DIRECTORY, source.format());
        assertEquals(List.of("first.log", "second.log"), source.entries().stream().sorted().collect(toList()));
    }

    @Test
    void rejectsOpeningDirectoryAsLines() throws IOException {
        GCLogSource source = GCLogSource.from(temporaryDirectory);

        assertThrows(IOException.class, source::lines);
    }

    @Test
    void rejectsNamedEntryForPlainText() throws IOException {
        GCLogSource source = GCLogSource.from(writePlainText("plain.log", "one\n"));

        assertThrows(IOException.class, () -> source.lines("entry.log"));
    }

    @Test
    void rejectsEmptyZip() throws IOException {
        GCLogSource source = GCLogSource.from(writeEmptyZip());

        assertThrows(IOException.class, source::lines);
    }

    @Test
    void rejectsMissingZipEntry() throws IOException {
        GCLogSource source = GCLogSource.from(writeZip());

        assertThrows(IOException.class, () -> source.lines("missing.log"));
    }

    private Path writePlainText(String name, String contents) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(name), contents);
    }

    private Path writeZip() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(log))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            writeZipEntry(output, "first.log", "one\ntwo\n");
            writeZipEntry(output, "second.log", "three\nfour\n");
        }
        return log;
    }

    private Path writeEmptyZip() throws IOException {
        Path log = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(log))) {
            return log;
        }
    }

    private static void writeZipEntry(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
