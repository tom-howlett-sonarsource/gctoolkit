// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

@SuppressWarnings("java:S5443")
public class LogSourceIOTest {

    private static final String PREFIX = "gctoolkit";
    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final String SECOND_LOG = "second.log";

    @TempDir
    private Path tempDir;

    @Test
    public void detectPlainTextFormat() throws IOException {
        Path log = tempDir.resolve(PREFIX + ".log");
        Files.write(log, List.of("line"));

        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceIO.detectFormat(log));
    }

    @Test
    public void detectDirectoryFormat() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve(PREFIX));

        assertEquals(LogSourceFormat.DIRECTORY, LogSourceIO.detectFormat(directory));
    }

    @Test
    public void streamPlainText() throws IOException {
        Path log = tempDir.resolve(PREFIX + ".log");
        Files.write(log, List.of(FIRST, SECOND));

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(LogSourceFormat.PLAINTEXT, log)));
    }

    @Test
    public void detectAndStreamGZip() throws IOException {
        Path log = tempDir.resolve(PREFIX + ".log.gz");
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(log))) {
            gzip.write((FIRST + "\n" + SECOND + "\n").getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(LogSourceFormat.GZIP, LogSourceIO.detectFormat(log));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(LogSourceFormat.GZIP, log)));
    }

    @Test
    public void detectAndStreamFirstZipEntry() throws IOException {
        Path log = tempDir.resolve(PREFIX + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(log))) {
            zip.putNextEntry(new ZipEntry("directory/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("gc.log"));
            zip.write((FIRST + "\n" + SECOND + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertEquals(LogSourceFormat.ZIP, LogSourceIO.detectFormat(log));
        assertEquals(List.of("gc.log"), LogSourceIO.zipEntryNames(log));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(LogSourceFormat.ZIP, log)));
    }

    @Test
    public void streamZipEntryByName() throws IOException {
        Path log = tempDir.resolve(PREFIX + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(log))) {
            zip.putNextEntry(new ZipEntry("first.log"));
            zip.write((FIRST + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(SECOND_LOG));
            zip.write((SECOND + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertEquals(List.of(SECOND), collect(LogSourceIO.streamZipEntry(log, SECOND_LOG)));
    }

    @Test
    public void listDirectoryEntries() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve(PREFIX));
        Path first = Files.createFile(directory.resolve("first.log"));
        Path second = Files.createFile(directory.resolve(SECOND_LOG));

        assertEquals(List.of(first, second), LogSourceIO.list(directory).stream().sorted().collect(Collectors.toList()));
    }

    @Test
    public void readTail() throws IOException {
        Path log = tempDir.resolve(PREFIX + ".log");
        Files.write(log, List.of(FIRST, SECOND, "third", "fourth"));

        assertEquals(List.of("third", "fourth"), LogSourceIO.tail(log, 2));
    }

    private List<String> collect(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }
}
