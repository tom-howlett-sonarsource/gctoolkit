package com.microsoft.gctoolkit.parser.io;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedSourceSafepointLogFileTest {
    @TempDir
    Path directory;

    @Test
    void zipSkipsDirectoriesAndReturnsUnmodifiedLinesWithoutSentinel() throws IOException {
        Path archive = directory.resolve("safepoints.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/safepoints.log"));
            output.write("  safepoint  \n\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (var lines = new SafepointLogFile(archive).stream()) {
            assertEquals(List.of("  safepoint  ", ""), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void missingSourceReportsIOException() {
        assertThrows(IOException.class,
                () -> new SafepointLogFile(directory.resolve("missing")).stream());
    }
}
