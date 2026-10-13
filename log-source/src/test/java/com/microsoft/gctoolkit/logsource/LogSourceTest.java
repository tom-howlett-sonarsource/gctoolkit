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
    void discoversAndOpensSourcesWithoutFilenameExtensions() throws IOException {
        byte[] content = " first \n\nlast\n".getBytes(StandardCharsets.UTF_8);
        Path plain = Files.write(directory.resolve("plain"), content);
        Path gzip = directory.resolve("compressed");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content);
        }
        Path zip = directory.resolve("archive");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("folder/first.log"));
            output.write(content);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSource.Format.PLAINTEXT, LogSource.discover(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.discover(gzip));
        assertEquals(LogSource.Format.ZIP, LogSource.discover(zip));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
        for (Path path : List.of(plain, gzip, zip)) {
            assertEquals(Files.size(path), LogSource.size(path));
            try (Stream<String> lines = LogSource.open(path)) {
                assertEquals(List.of(" first ", "", "last"), lines.collect(Collectors.toList()));
            }
        }
        assertEquals(3, LogSource.list(directory).size());
        assertEquals(List.of("folder/first.log", "second.log"), LogSource.zipEntries(zip));
        try (Stream<String> lines = LogSource.openZipEntry(zip, "second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSource.openZipEntry(zip, "missing"));
    }

    @Test
    void emptyAndDirectoryOnlyArchivesHaveNoLines() throws IOException {
        for (boolean withDirectory : List.of(false, true)) {
            Path zip = directory.resolve("empty-" + withDirectory);
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
                if (withDirectory) {
                    output.putNextEntry(new ZipEntry("folder/"));
                }
            }
            try (Stream<String> lines = LogSource.open(zip)) {
                assertEquals(0, lines.count());
            }
        }
    }
}
