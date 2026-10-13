package com.microsoft.gctoolkit.logsource;

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
    void discoversDirectChildrenAndMeasuresBytes() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, "é\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("other.log"), "other");
        Files.createDirectories(directory.resolve("nested"));
        Files.writeString(directory.resolve("nested/gc.log"), "nested");
        assertEquals(List.of(plain), LogSource.discover(directory, "gc."));
        assertEquals(3, LogSource.byteSize(plain));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(directory));
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.format(plain));
        assertEquals(List.of("é"), read(plain));
    }

    @Test
    void detectsGzipByContentRatherThanExtension() throws IOException {
        Path gzip = directory.resolve("compressed.log");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("first\n\n second \n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSource.Format.GZIP, LogSource.format(gzip));
        assertEquals(Files.size(gzip), LogSource.byteSize(gzip));
        assertEquals(List.of("first", "", " second "), read(gzip));
    }

    @Test
    void skipsZipDirectoriesAndReadsOnlyFirstFileOrNamedEntry() throws IOException {
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
        assertEquals(List.of("first"), read(zip));
        try (Stream<String> lines = LogSource.zipEntryLines(zip, "second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.zipEntryLines(zip, "missing"));
        Files.delete(zip);
    }

    @Test
    void emptyAndDirectoryOnlyZipsHaveNoLines() throws IOException {
        Path zip = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.finish();
        }
        assertEquals(List.of(), read(zip));
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(List.of(), read(zip));
    }

    @Test
    void reportsUnreadableAndUnsupportedSources() {
        assertThrows(IOException.class, () -> LogSource.lines(directory.resolve("missing")));
        assertThrows(IOException.class, () -> LogSource.lines(directory));
        assertThrows(IOException.class, () -> LogSource.byteSize(directory.resolve("missing")));
    }

    private List<String> read(Path path) throws IOException {
        try (Stream<String> lines = LogSource.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }
}
