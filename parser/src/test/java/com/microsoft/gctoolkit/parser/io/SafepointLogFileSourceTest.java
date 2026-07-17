package com.microsoft.gctoolkit.parser.io;

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

class SafepointLogFileSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGzipSources() throws IOException {
        Path plain = Files.writeString(temporaryDirectory.resolve("plain.log"), "first\nsecond\n");
        Path zip = writeZip("compressed.zip", "first\nsecond\n");
        Path gzip = writeGzip("compressed.gz", "first\nsecond\n");
        List<String> expected = List.of("first", "second");

        assertEquals(expected, lines(plain));
        assertEquals(expected, lines(zip));
        assertEquals(expected, lines(gzip));
    }

    private List<String> lines(Path path) throws IOException {
        try (var stream = new SafepointLogFile(path).stream()) {
            return stream.collect(toList());
        }
    }

    private Path writeGzip(String name, String contents) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String name, String contents) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}
