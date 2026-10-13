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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversFormatsByContentAndSizesPhysicalBytes() throws IOException {
        Path plain = directory.resolve("plain.gz");
        Files.writeString(plain, "first\nsecond\n", StandardCharsets.UTF_8);
        Path gzip = directory.resolve("compressed.log");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(directory));
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.format(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.format(gzip));
        assertEquals(13, LogSource.byteSize(plain));
        assertEquals(Files.size(gzip), LogSource.byteSize(gzip));
        assertEquals(2, LogSource.files(directory).size());
        assertLines(plain, List.of("first", "second"));
        assertLines(gzip, List.of("first", "second"));
    }

    @Test
    void skipsDirectoriesAndReadsOnlyFirstZipFileOrNamedEntry() throws IOException {
        Path zip = directory.resolve("archive.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeEntry(output, "logs/", "");
            writeEntry(output, "logs/first.log", "first\n");
            writeEntry(output, "logs/second.log", "second\n");
        }
        assertEquals(LogSource.Format.ZIP, LogSource.format(zip));
        assertEquals(List.of("logs/first.log", "logs/second.log"), LogSource.zipEntries(zip));
        assertLines(zip, List.of("first"));
        try (Stream<String> lines = LogSource.zipEntryLines(zip, "logs/second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.zipEntryLines(zip, "missing.log"));
    }

    @Test
    void emptyAndDirectoryOnlyArchivesHaveNoLines() throws IOException {
        Path zip = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.finish();
        }
        assertLines(zip, List.of());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeEntry(output, "logs/", "");
        }
        assertTrue(LogSource.zipEntries(zip).isEmpty());
        assertLines(zip, List.of());
        Path empty = directory.resolve("empty.log");
        Files.createFile(empty);
        assertLines(empty, List.of());
    }

    @Test
    void unreadableSourcesFailWithIoExceptions() {
        Path missing = directory.resolve("missing");
        assertThrows(IOException.class, () -> LogSource.lines(missing));
        assertThrows(IOException.class, () -> LogSource.byteSize(missing));
        assertThrows(IOException.class, () -> LogSource.lines(directory));
    }

    private void assertLines(Path path, List<String> expected) throws IOException {
        try (Stream<String> lines = LogSource.lines(path)) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
    }

    private void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
