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

class LogSourcesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversDirectoryFilesAndReportsTheirSizes() throws IOException {
        Files.writeString(temporaryDirectory.resolve("gc.log"), "first\n", StandardCharsets.UTF_8);
        Files.writeString(temporaryDirectory.resolve("gc.log.1"), "second\n", StandardCharsets.UTF_8);
        Files.createDirectory(temporaryDirectory.resolve("ignored"));

        List<LogSource> sources = LogSources.discover(temporaryDirectory);

        assertEquals(List.of("gc.log", "gc.log.1"), sources.stream()
                .map(LogSource::getName)
                .sorted()
                .collect(Collectors.toList()));
        assertEquals(13L, sources.stream().mapToLong(LogSource::size).sum());
    }

    @Test
    void discoversZipEntriesAndReportsUncompressedSizes() throws IOException {
        Path zip = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/gc.log", "first\n");
            writeEntry(output, "logs/gc.log.1", "second\n");
        }

        List<LogSource> sources = LogSources.discover(zip);

        assertEquals(List.of("logs/gc.log", "logs/gc.log.1"), sources.stream()
                .map(LogSource::getName)
                .collect(Collectors.toList()));
        assertEquals(13L, sources.stream().mapToLong(LogSource::size).sum());
    }

    @Test
    void opensPlainZipAndGzipSources() throws IOException {
        Path plain = temporaryDirectory.resolve("gc.log");
        Files.writeString(plain, "plain\n", StandardCharsets.UTF_8);

        Path zip = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/gc.log", "zip\n");
        }

        Path gzip = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(List.of("plain"), readLines(LogSources.first(plain)));
        assertEquals(List.of("zip"), readLines(LogSources.first(zip)));
        assertEquals(List.of("gzip"), readLines(LogSources.first(gzip)));
    }

    private static List<String> readLines(LogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private static void writeEntry(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
