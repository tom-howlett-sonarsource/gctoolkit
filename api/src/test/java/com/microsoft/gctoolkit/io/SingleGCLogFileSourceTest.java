package com.microsoft.gctoolkit.io;

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

class SingleGCLogFileSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGzipSourcesConsistently() throws IOException {
        Path plain = Files.writeString(temporaryDirectory.resolve("plain.log"), " first \n\nsecond\n");
        Path zip = writeZip("compressed.zip", " first \n\nsecond\n");
        Path gzip = writeGzip("compressed.gz", " first \n\nsecond\n");
        List<String> expected = List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL);

        assertEquals(expected, lines(plain));
        assertEquals(expected, lines(zip));
        assertEquals(expected, lines(gzip));
    }

    private List<String> lines(Path path) throws IOException {
        try (var stream = new SingleGCLogFile(path).stream()) {
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
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}
