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
    void discoversSourcesAndReportsStoredBytes() throws IOException {
        Path plain = Files.writeString(directory.resolve("gc.log"), "  line  \n\n");
        assertEquals(List.of(plain), LogSource.discover(directory));
        assertEquals(Files.size(plain), LogSource.size(plain));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(directory));
        assertEquals(List.of("  line  ", ""), collect(LogSource.lines(plain)));
    }

    @Test
    void detectsGzipByContentAndPreservesLines() throws IOException {
        Path gzip = directory.resolve("no-extension");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("  line  \n\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSource.Format.GZIP, LogSource.format(gzip));
        assertEquals(List.of("  line  ", ""), collect(LogSource.lines(gzip)));
    }

    @Test
    void supportsFirstNamedAndAllZipEntries() throws IOException {
        Path zip = directory.resolve("archive");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("folder/first.log"));
            output.write("first\npartial".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertEquals(LogSource.Format.ZIP, LogSource.format(zip));
        assertEquals(List.of("folder/first.log", "second.log"), LogSource.zipEntries(zip));
        assertEquals(List.of("first", "partial"), collect(LogSource.lines(zip)));
        assertEquals(List.of("second"), collect(LogSource.zipEntryLines(zip, "second.log")));
        assertEquals(List.of("first", "partialsecond"), collect(LogSource.allZipLines(zip)));
        assertThrows(IOException.class, () -> LogSource.zipEntryLines(zip, "missing"));
    }

    @Test
    void emptyAndDirectoryOnlyArchivesReturnEmptyStreams() throws IOException {
        Path empty = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(empty))) {
            output.finish();
        }
        assertEquals(List.of(), collect(LogSource.lines(empty)));
        Path folders = directory.resolve("folders.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(folders))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
        }
        assertEquals(List.of(), collect(LogSource.lines(folders)));
        assertEquals(List.of(), collect(LogSource.allZipLines(folders)));
    }

    @Test
    void missingSourcesAndMalformedCompressionFailWithIOException() throws IOException {
        assertThrows(IOException.class, () -> LogSource.lines(directory.resolve("missing")));
        Path broken = Files.write(directory.resolve("broken"), new byte[] {0x1f, (byte) 0x8b});
        assertThrows(IOException.class, () -> LogSource.lines(broken));
        assertThrows(IOException.class, () -> LogSource.lines(directory));
    }

    private List<String> collect(Stream<String> lines) {
        try (Stream<String> source = lines) {
            return source.collect(Collectors.toList());
        }
    }
}
