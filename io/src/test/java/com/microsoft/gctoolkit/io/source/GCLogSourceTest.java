package com.microsoft.gctoolkit.io.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversSourceFormats() throws IOException {
        Path plain = writePlain("plain.log", "one\ntwo\n");
        Path gzip = writeGzip("gzip.log.gz", "one\ntwo\n");
        Path zip = writeZip("zip.log.zip", "logs/", "logs/gc.log", "one\ntwo\n");

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip));
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(temporaryDirectory));
    }

    @Test
    void returnsSourceByteSize() throws IOException {
        Path source = writePlain("sized.log", "123456789");

        assertEquals(9L, GCLogSource.size(source));
    }

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        Path plain = writePlain("plain.log", "one\ntwo\n");
        Path gzip = writeGzip("gzip.log.gz", "one\ntwo\n");
        Path zip = writeZip("zip.log.zip", "logs/", "logs/gc.log", "one\ntwo\n");

        assertEquals(List.of("one", "two"), read(GCLogSource.open(plain)));
        assertEquals(List.of("one", "two"), read(GCLogSource.open(gzip)));
        assertEquals(List.of("one", "two"), read(GCLogSource.open(zip)));
    }

    @Test
    void discoversAndOpensNamedZipEntries() throws IOException {
        Path zip = writeZip("zip.log.zip", "logs/", "logs/gc.log", "one\ntwo\n");

        assertEquals(List.of("logs/gc.log"), GCLogSource.zipEntries(zip));
        assertEquals(List.of("one", "two"), read(GCLogSource.openZipEntry(zip, "logs/gc.log")));
    }

    @Test
    void listsDirectorySources() throws IOException {
        Path first = writePlain("first.log", "one\n");
        Path second = writePlain("second.log", "two\n");

        assertEquals(List.of(first, second), GCLogSource.list(temporaryDirectory));
    }

    @Test
    void rejectsDirectoriesAsLineSources() {
        assertThrows(IOException.class, () -> GCLogSource.open(temporaryDirectory));
    }

    @Test
    void rejectsMissingZipEntries() throws IOException {
        Path zip = writeZip("zip.log.zip", "logs/", "logs/gc.log", "one\ntwo\n");

        assertThrows(IOException.class, () -> GCLogSource.openZipEntry(zip, "missing.log"));
    }

    private Path writePlain(String fileName, String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(fileName), content);
    }

    private Path writeGzip(String fileName, String content) throws IOException {
        Path target = temporaryDirectory.resolve(fileName);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(target))) {
            output.write(content.getBytes(UTF_8));
        }
        return target;
    }

    private Path writeZip(String fileName, String directory, String entryName, String content) throws IOException {
        Path target = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            output.putNextEntry(new ZipEntry(directory));
            output.closeEntry();
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content.getBytes(UTF_8));
            output.closeEntry();
        }
        return target;
    }

    private List<String> read(Stream<String> stream) {
        try (stream) {
            return stream.collect(toList());
        }
    }
}
