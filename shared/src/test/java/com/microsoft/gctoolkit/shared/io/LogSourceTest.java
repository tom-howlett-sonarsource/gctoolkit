package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainAndGzipFilesInDirectory() throws IOException {
        Path plain = writePlain("gc.log", "plain\n");
        Path gzip = writeGzip("gc.log.gz", "gzip\n");

        List<LogSource> sources = LogSource.discover(temporaryDirectory);

        assertEquals(List.of(plain, gzip), sources.stream().map(LogSource::path).sorted().collect(Collectors.toList()));
    }

    @Test
    void discoversEachNonDirectoryZipEntry() throws IOException {
        Path zip = writeZip();

        List<LogSource> sources = LogSource.discover(zip);

        assertEquals(List.of("logs/first.log", "second.log"), sources.stream().map(LogSource::entryName).collect(Collectors.toList()));
    }

    @Test
    void opensPlainGzipAndZipLines() throws IOException {
        Path plain = writePlain("plain.log", "first\nsecond\n");
        Path gzip = writeGzip("gzip.log.gz", "first\nsecond\n");
        Path zip = writeZip();

        assertEquals(List.of("first", "second"), lines(LogSource.of(plain)));
        assertEquals(List.of("first", "second"), lines(LogSource.of(gzip)));
        assertEquals(List.of("first", "second"), lines(LogSource.discover(zip).get(0)));
    }

    @Test
    void reportsUncompressedContentSize() throws IOException {
        byte[] content = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path plain = writePlain("plain.log", new String(content, StandardCharsets.UTF_8));
        Path gzip = writeGzip("gzip.log.gz", new String(content, StandardCharsets.UTF_8));
        Path zip = writeZip();

        assertEquals(content.length, LogSource.of(plain).size());
        assertEquals(content.length, LogSource.of(gzip).size());
        assertEquals(content.length, LogSource.discover(zip).get(0).size());
    }

    @Test
    void opensEmptyZipAsEmptySource() throws IOException {
        Path zip = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(zip))) {
        }

        LogSource source = LogSource.of(zip);

        assertEquals(0, source.size());
        assertEquals(List.of(), lines(source));
    }

    @Test
    void detectsFormatsAndExposesSourceProperties() throws IOException {
        Path plain = writePlain("plain.log", "plain\n");
        Path gzip = writeGzip("gzip.log.gz", "gzip\n");
        Path zip = writeZip();

        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(temporaryDirectory));
        assertEquals(LogSource.Format.PLAIN_TEXT, LogSource.of(plain).format());
        assertEquals(LogSource.Format.GZIP, LogSource.of(gzip).format());
        assertEquals(LogSource.Format.ZIP, LogSource.of(zip).format());
        assertEquals(plain, LogSource.of(plain).path());
        assertNull(LogSource.of(plain).entryName());
    }

    @Test
    void selectsNamedZipEntry() throws IOException {
        Path zip = writeZip();

        assertEquals(List.of("third"), lines(LogSource.zipEntry(zip, "second.log")));
        assertThrows(IOException.class, () -> LogSource.zipEntry(zip, "missing.log"));
        assertThrows(IOException.class, () -> LogSource.zipEntry(writePlain("not.zip", "plain"), "entry"));
    }

    @Test
    void returnsSinglePathAndRejectsDirectoryAsSource() throws IOException {
        Path plain = writePlain("plain.log", "plain\n");

        assertEquals(List.of(plain), LogSource.discoverPaths(plain));
        assertThrows(IOException.class, () -> LogSource.of(temporaryDirectory));
    }

    private List<String> lines(LogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain(String name, String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(name), content);
    }

    private Path writeGzip(String name, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = temporaryDirectory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/first.log", "first\nsecond\n");
            writeEntry(output, "second.log", "third\n");
        }
        return path;
    }

    private void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
