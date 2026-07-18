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
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogSourceIOTest {

    private static final String FIRST = "first";
    private static final String SECOND = "second";

    @TempDir
    private Path tempDirectory;

    @Test
    void discoversFormatsAndByteSizes() throws IOException {
        Path plain = writePlain("gc.log", FIRST, SECOND);
        Path gzip = writeGZip("gc.log.gz", FIRST, SECOND);
        Path zip = writeZip("gc.zip", "gc.log", FIRST, SECOND);

        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceIO.detectFormat(plain));
        assertEquals(LogSourceFormat.GZIP, LogSourceIO.detectFormat(gzip));
        assertEquals(LogSourceFormat.ZIP, LogSourceIO.detectFormat(zip));
        assertEquals(LogSourceFormat.DIRECTORY, LogSourceIO.detectFormat(tempDirectory));
        assertEquals(Files.size(plain), LogSourceIO.byteSize(plain));
        assertEquals(Files.size(gzip), LogSourceIO.byteSize(gzip));
        assertEquals(Files.size(zip), LogSourceIO.byteSize(zip));
        assertEquals(Files.size(plain) + Files.size(gzip) + Files.size(zip), LogSourceIO.byteSize(tempDirectory));
    }

    @Test
    void discoversDirectoryAndZipSources() throws IOException {
        Path first = Files.createFile(tempDirectory.resolve("first.log"));
        Path second = Files.createFile(tempDirectory.resolve("second.log"));
        Path zip = tempDirectory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            writeZipEntry(output, "first.log", FIRST);
            writeZipEntry(output, "second.log", SECOND);
        }

        assertEquals(Stream.of(first, second, zip).sorted().collect(Collectors.toList()),
                LogSourceIO.list(tempDirectory).stream().sorted().collect(Collectors.toList()));
        assertEquals(List.of("first.log", "second.log"), LogSourceIO.zipEntryNames(zip));
    }

    @Test
    void opensPlainZipAndGZipStreams() throws IOException {
        Path plain = writePlain("gc.log", FIRST, SECOND);
        Path gzip = writeGZip("gc.log.gz", FIRST, SECOND);
        Path zip = writeZip("gc.zip", "gc.log", FIRST, SECOND);

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(plain, LogSourceFormat.PLAINTEXT)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(zip, LogSourceFormat.ZIP)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(gzip, LogSourceFormat.GZIP)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(LogSourceFormat.PLAINTEXT, plain)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.streamZipEntry(zip, "gc.log")));
    }

    @Test
    void rejectsUnsupportedOrEmptySources() throws IOException {
        Path emptyZip = tempDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // Empty archive.
        }

        assertThrows(IOException.class, () -> LogSourceIO.stream(tempDirectory, LogSourceFormat.DIRECTORY));
        assertEquals(List.of(), collect(LogSourceIO.stream(emptyZip, LogSourceFormat.ZIP)));
        assertThrows(IOException.class, () -> LogSourceIO.streamZipEntry(emptyZip, "missing.log"));
    }

    private Path writePlain(String fileName, String... lines) throws IOException {
        return Files.write(tempDirectory.resolve(fileName), List.of(lines));
    }

    private Path writeGZip(String fileName, String... lines) throws IOException {
        Path path = tempDirectory.resolve(fileName);
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content(lines));
        }
        return path;
    }

    private Path writeZip(String fileName, String entryName, String... lines) throws IOException {
        Path path = tempDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            writeZipEntry(output, entryName, lines);
        }
        return path;
    }

    private void writeZipEntry(ZipOutputStream output, String entryName, String... lines) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(content(lines));
        output.closeEntry();
    }

    private byte[] content(String... lines) {
        return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private List<String> collect(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }
}
