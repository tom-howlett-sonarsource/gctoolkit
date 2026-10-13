package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversSourcesAndReportsPhysicalByteSize() throws IOException {
        Path plain = Files.writeString(directory.resolve("plain.gz"), "  first  \n\nsecond\n");
        Path gzip = directory.resolve("compressed.log");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("  first  \n\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(directory));
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.format(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.format(gzip));
        assertEquals(Files.size(plain), LogSource.size(plain));
        assertEquals(Files.size(gzip), LogSource.size(gzip));
        assertEquals(2, LogSource.discover(directory).size());
        assertTrue(LogSource.discover(directory).containsAll(List.of(plain, gzip)));
        for (Path path : List.of(plain, gzip)) {
            try (var lines = LogSource.lines(path)) {
                assertEquals(List.of("  first  ", "", "second"), lines.collect(Collectors.toList()));
            }
        }
    }

    @Test
    void skipsDirectoriesAndReadsOnlyFirstZipFileOrNamedEntry() throws IOException {
        Path zip = directory.resolve("archive.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/first.log"));
            output.write("first\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertEquals(LogSource.Format.ZIP, LogSource.format(zip));
        assertEquals(List.of("logs/first.log", "second.log"), LogSource.zipEntries(zip));
        try (var lines = LogSource.lines(zip)) {
            assertEquals(List.of("first"), lines.collect(Collectors.toList()));
        }
        try (var lines = LogSource.zipLines(zip, "second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.zipLines(zip, "missing"));
    }

    @Test
    void handlesEmptyAndDirectoryOnlyArchives() throws IOException {
        for (boolean withDirectory : List.of(false, true)) {
            Path zip = directory.resolve("empty-" + withDirectory + ".zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
                if (withDirectory) {
                    output.putNextEntry(new ZipEntry("logs/"));
                    output.closeEntry();
                }
            }
            try (var lines = LogSource.lines(zip)) {
                assertEquals(0, lines.count());
            }
            assertTrue(LogSource.zipEntries(zip).isEmpty());
        }
    }

    @Test
    void handlesShortFilesAndPropagatesIoFailures() throws IOException {
        Path empty = Files.createFile(directory.resolve("empty"));
        Path shortFile = Files.write(directory.resolve("short"), new byte[] {0x1f});
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.format(empty));
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.format(shortFile));
        assertThrows(IOException.class, () -> LogSource.lines(directory));
        assertThrows(IOException.class, () -> LogSource.lines(directory.resolve("missing")));
        assertThrows(IOException.class, () -> LogSource.lines(shortFile, LogSource.Format.GZIP));
    }
}
