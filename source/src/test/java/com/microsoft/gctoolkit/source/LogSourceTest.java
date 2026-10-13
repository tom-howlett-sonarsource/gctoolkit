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
    void discoversAndReadsPlainAndGzipWithoutFilenameExtensions() throws IOException {
        Path plain = directory.resolve("plain");
        Files.writeString(plain, "  line  \n\n");
        Path gzip = directory.resolve("compressed");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("  line  \n\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.discover(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.discover(gzip));
        assertEquals(Files.size(gzip), LogSource.size(gzip));
        assertEquals(2, LogSource.list(directory).size());
        for (Path path : List.of(plain, gzip)) {
            try (Stream<String> lines = LogSource.lines(path)) {
                assertEquals(List.of("  line  ", ""), lines.collect(Collectors.toList()));
            }
        }
    }

    @Test
    void skipsZipDirectoriesAndSelectsFirstOrNamedEntry() throws IOException {
        Path zip = directory.resolve("archive");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            for (String name : List.of("first", "second")) {
                output.putNextEntry(new ZipEntry(name));
                output.write((name + "\n").getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        assertEquals(LogSource.Format.ZIP, LogSource.discover(zip));
        assertEquals(List.of("first", "second"), LogSource.zipEntries(zip));
        try (Stream<String> lines = LogSource.lines(zip)) {
            assertEquals(List.of("first"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = LogSource.zipLines(zip, "second")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = LogSource.allZipLines(zip)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.zipLines(zip, "missing"));
    }

    @Test
    void handlesEmptyZipAndRejectsDirectoriesAndMissingSources() throws IOException {
        Path zip = directory.resolve("empty");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        try (Stream<String> lines = LogSource.lines(zip)) {
            assertEquals(0, lines.count());
        }
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
        assertThrows(IOException.class, () -> LogSource.lines(directory));
        assertThrows(IOException.class, () -> LogSource.lines(directory.resolve("missing")));
    }
}
