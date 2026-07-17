package com.microsoft.gctoolkit.shared.io;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static java.util.stream.Collectors.toList;

class LogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversSourcesByContentAndDirectory() throws IOException {
        Path plain = writePlain("plain.data", "plain");
        Path gzip = writeGzip("gzip.data", "gzip");
        Path zip = writeZip("zip.data", List.of(new Entry("log.txt", "zip")));

        assertEquals(LogSourceType.PLAIN_TEXT, LogSource.discover(plain));
        assertEquals(LogSourceType.GZIP, LogSource.discover(gzip));
        assertEquals(LogSourceType.ZIP, LogSource.discover(zip));
        assertEquals(LogSourceType.DIRECTORY, LogSource.discover(temporaryDirectory));
    }

    @Test
    void reportsSourceByteSize() throws IOException {
        Path plain = writePlain("plain.log", "four");

        assertEquals(Files.size(plain), LogSource.byteSize(plain));
    }

    @Test
    void opensPlainTextLines() throws IOException {
        Path plain = writePlain("plain.log", "first\nsecond\n");

        try (var lines = LogSource.lines(plain)) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void opensGzipLines() throws IOException {
        Path gzip = writeGzip("compressed.log", "first\nsecond\n");

        try (var lines = LogSource.lines(gzip)) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void opensFirstNonDirectoryZipEntry() throws IOException {
        Path zip = writeZip("compressed.log", List.of(
                new Entry("logs/", null),
                new Entry("logs/first.log", "first\nsecond\n"),
                new Entry("logs/ignored.log", "ignored\n")));

        try (var lines = LogSource.lines(zip)) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void opensNamedZipEntry() throws IOException {
        Path zip = writeZip("rotating.zip", List.of(
                new Entry("first.log", "first\n"),
                new Entry("second.log", "second\n")));

        try (var lines = LogSource.lines(zip, "second.log")) {
            assertEquals(List.of("second"), lines.collect(toList()));
        }
    }

    @Test
    void rejectsDirectoriesAsLineSources() {
        assertThrows(IOException.class, () -> LogSource.lines(temporaryDirectory));
    }

    @Test
    void rejectsMissingZipEntries() throws IOException {
        Path zip = writeZip("rotating.zip", List.of(new Entry("first.log", "first\n")));

        assertThrows(IOException.class, () -> LogSource.lines(zip, "missing.log"));
    }

    private Path writePlain(String name, String contents) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(name), contents, StandardCharsets.UTF_8);
    }

    private Path writeGzip(String name, String contents) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String name, List<Entry> entries) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Entry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                if (entry.contents != null) {
                    output.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return path;
    }

    private static final class Entry {
        private final String name;
        private final String contents;

        private Entry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}
