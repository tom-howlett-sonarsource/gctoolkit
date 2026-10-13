package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedRotatingSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversAndStreamsDirectoryAndArchiveSegments() throws IOException {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.writeString(logs.resolve("gc.log"), "  entry  \n\n");
        Path zip = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("  entry  \n\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        for (Path source : List.of(logs, zip)) {
            RotatingGCLogFile log = new RotatingGCLogFile(source);
            assertEquals(1, log.getMetaData().getNumberOfFiles());
            assertEquals(1, log.getOrderedGarbageCollectionLogFiles().size());
            try (var lines = log.stream()) {
                assertEquals(List.of("entry", GCLogFile.END_OF_DATA_SENTINEL),
                        lines.collect(Collectors.toList()));
            }
        }
    }
}
