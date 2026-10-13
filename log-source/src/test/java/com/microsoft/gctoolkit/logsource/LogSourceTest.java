package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
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
    void discoversSourcesAndReportsStoredByteSizes() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, "line\n");
        assertEquals(List.of(plain), LogSource.discover(directory));
        assertEquals(5, LogSource.byteSize(plain));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(directory));
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.format(plain));
        assertThrows(IOException.class, () -> LogSource.lines(directory));
        assertThrows(IOException.class, () -> LogSource.lines(directory.resolve("missing")));
    }

    @Test
    void detectsGzipByContentAndPreservesRawLines() throws IOException {
        Path gzip = directory.resolve("not-a-gzip-extension.log");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("  first  \n\nsecond\n".getBytes(Charset.defaultCharset()));
        }
        assertEquals(LogSource.Format.GZIP, LogSource.format(gzip));
        assertEquals(Files.size(gzip), LogSource.byteSize(gzip));
        assertEquals(List.of("  first  ", "", "second"), read(gzip));
    }

    @Test
    void skipsDirectoriesAndReadsOnlyFirstZipFileOrNamedEntry() throws IOException {
        Path zip = directory.resolve("archive.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/first.log"));
            output.write("first\n".getBytes(Charset.defaultCharset()));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("second\n".getBytes(Charset.defaultCharset()));
            output.closeEntry();
        }
        assertEquals(LogSource.Format.ZIP, LogSource.format(zip));
        assertEquals(List.of("logs/first.log", "second.log"), LogSource.zipEntries(zip));
        assertEquals(List.of("first"), read(zip));
        try (Stream<String> lines = LogSource.zipLines(zip, "second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.zipLines(zip, "missing"));
    }

    @Test
    void emptyAndDirectoryOnlyZipSourcesProduceEmptyStreams() throws IOException {
        Path zip = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.finish();
        }
        assertTrue(read(zip).isEmpty());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertTrue(read(zip).isEmpty());
        assertTrue(LogSource.zipEntries(zip).isEmpty());
    }

    @Test
    void malformedGzipFailsDuringOpening() throws IOException {
        Path gzip = directory.resolve("broken.gz");
        Files.write(gzip, new byte[]{0x1f, (byte) 0x8b});
        assertThrows(IOException.class, () -> LogSource.lines(gzip));
    }

    private List<String> read(Path path) throws IOException {
        try (Stream<String> lines = LogSource.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }
}
