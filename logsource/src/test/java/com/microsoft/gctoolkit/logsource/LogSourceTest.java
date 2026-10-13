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

class LogSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversFormatsByContentAndPreservesRawLines() throws IOException {
        String content = "  first  \n\nsecond\n";
        Path plain = directory.resolve("plain.gz");
        Files.writeString(plain, content);
        Path gzip = directory.resolve("compressed.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.discover(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.discover(gzip));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
        assertEquals(Files.size(gzip), LogSource.size(gzip));
        assertEquals(2, LogSource.list(directory).size());
        for (Path source : List.of(plain, gzip)) {
            try (Stream<String> lines = LogSource.lines(source)) {
                assertEquals(List.of("  first  ", "", "second"), lines.collect(Collectors.toList()));
            }
        }
    }

    @Test
    void skipsZipDirectoriesAndOpensOnlyTheFirstFileOrNamedEntry() throws IOException {
        Path zip = directory.resolve("archive.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/first.log"));
            output.write("first\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/second.log"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertEquals(LogSource.Format.ZIP, LogSource.discover(zip));
        assertEquals(List.of("logs/first.log", "logs/second.log"), LogSource.zipEntries(zip));
        try (Stream<String> lines = LogSource.lines(zip)) {
            assertEquals(List.of("first"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = LogSource.zipEntryLines(zip, "logs/second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.zipEntryLines(zip, "missing"));
    }

    @Test
    void emptyZipAndMissingSourcesHaveDefinedBehavior() throws IOException {
        Path zip = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        try (Stream<String> lines = LogSource.lines(zip)) {
            assertEquals(0, lines.count());
        }
        assertThrows(IOException.class, () -> LogSource.lines(directory.resolve("missing")));
        assertThrows(IOException.class, () -> LogSource.lines(directory));
    }
}
