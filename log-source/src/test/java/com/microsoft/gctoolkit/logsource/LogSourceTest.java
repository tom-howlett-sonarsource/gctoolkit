package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
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
    void discoversAndSizesSources() throws IOException {
        Path plain = directory.resolve("plain");
        Files.writeString(plain, "  log  \n\n", StandardCharsets.UTF_8);
        assertEquals(List.of(plain), LogSource.discover(directory));
        assertEquals(9, LogSource.size(plain));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(directory));
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.format(plain));
        try (Stream<String> lines = LogSource.lines(plain)) {
            assertEquals(List.of("  log  ", ""), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void detectsGzipByContentAndPreservesRawLines() throws IOException {
        Path gzip = directory.resolve("no-extension");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("  log  \n\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSource.Format.GZIP, LogSource.format(gzip));
        assertTrue(LogSource.hasMagic(gzip, 0x1f, 0x8b));
        try (Stream<String> lines = LogSource.lines(gzip)) {
            assertEquals(List.of("  log  ", ""), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void supportsFirstNamedAndAllZipEntries() throws IOException {
        Path zip = directory.resolve("archive");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("folder/first"));
            output.write("first".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertEquals(LogSource.Format.ZIP, LogSource.format(zip));
        assertEquals(List.of("folder/first", "second"), LogSource.zipEntries(zip));
        try (Stream<String> lines = LogSource.lines(zip)) {
            assertEquals(List.of("first"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = LogSource.zipEntryLines(zip, "second")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = LogSource.allZipLines(zip)) {
            assertEquals(List.of("firstsecond"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.zipEntryLines(zip, "missing"));
    }

    @Test
    void handlesEmptyArchivesAndRejectsInvalidSources() throws IOException {
        Path zip = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
        }
        try (Stream<String> lines = LogSource.lines(zip)) {
            assertEquals(0, lines.count());
        }
        assertThrows(IOException.class, () -> LogSource.lines(directory));
        assertThrows(IOException.class, () -> LogSource.lines(directory.resolve("missing")));
        assertThrows(IOException.class, () -> LogSource.gzipLines(zip));
    }
}
