package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.SingleGCLogFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedLogSourceCallersTest {
    @TempDir
    Path directory;

    @Test
    void sharesZipOpeningButPreservesEachCallersLinePolicy() throws IOException {
        Path path = directory.resolve("log.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write("  log line  \n\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        SingleGCLogFile gcLog = new SingleGCLogFile(path);
        assertTrue(gcLog.getMetaData().isZip());
        try (Stream<String> lines = gcLog.stream()) {
            assertEquals(List.of("log line", gcLog.endOfData()), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("  log line  ", ""), lines.collect(Collectors.toList()));
        }
    }
}
