package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.io.SingleGCLogFile;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedLogSourceTest {
    @TempDir
    Path directory;

    @Test
    void sharesZipIoWithoutSharingConsumerFilteringPolicy() throws IOException {
        Path zip = directory.resolve("source.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("  first  \n\nsecond\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        SingleGCLogFile gcLog = new SingleGCLogFile(zip);
        assertTrue(gcLog.getMetaData().isZip());
        assertEquals(1, gcLog.getMetaData().getNumberOfFiles());
        try (var lines = gcLog.stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        try (var lines = new SafepointLogFile(zip).stream()) {
            assertEquals(List.of("  first  ", "", "second"), lines.collect(Collectors.toList()));
        }
    }
}
