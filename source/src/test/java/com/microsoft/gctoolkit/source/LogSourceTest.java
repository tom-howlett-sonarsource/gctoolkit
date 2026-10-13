package com.microsoft.gctoolkit.source;

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

class LogSourceTest {
    @TempDir
    Path directory;

    @Test
    void opensSourcesByContentAndPreservesLines() throws IOException {
        byte[] content = " first \n\nlast\n".getBytes(StandardCharsets.UTF_8);
        Path plain = Files.write(directory.resolve("plain.zip"), content);
        Path gzip = directory.resolve("gzip.log");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content);
        }
        Path zip = directory.resolve("archive.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("folder/first.log"));
            output.write(content);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
        }
        for (Path path : List.of(plain, gzip, zip)) {
            try (Stream<String> lines = LogSource.open(path)) {
                assertEquals(List.of(" first ", "", "last"), lines.collect(Collectors.toList()));
            }
        }
        assertEquals(List.of("folder/first.log", "second.log"), LogSource.zipEntries(zip));
        try (Stream<String> lines = LogSource.openZipEntry(zip, "second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.openZipEntry(zip, "missing"));
        assertEquals(3, LogSource.discover(directory).size());
        assertEquals(Files.size(plain) + Files.size(gzip) + Files.size(zip), LogSource.size(directory));
    }

    @Test
    void emptyZipAndDirectoryOnlyZipHaveNoLines() throws IOException {
        for (boolean includeDirectory : List.of(false, true)) {
            Path zip = directory.resolve("empty" + includeDirectory);
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
                if (includeDirectory) {
                    output.putNextEntry(new ZipEntry("folder/"));
                }
            }
            try (Stream<String> lines = LogSource.open(zip)) {
                assertEquals(0, lines.count());
            }
        }
    }
}
