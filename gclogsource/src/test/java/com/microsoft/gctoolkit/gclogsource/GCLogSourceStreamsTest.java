// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceStreamsTest {

    @Test
    void streamsPlaintextLines() throws IOException {
        Path file = Files.createTempFile("gclogsource-streams", ".log");
        Files.write(file, List.of(" first ", "", "second"));

        try (var lines = GCLogSourceStreams.lines(file, GCLogSourceFormat.PLAINTEXT)) {
            assertEquals(List.of("first", "second"), GCLogSourceStreams.normalized(lines).collect(Collectors.toList()));
        }
    }

    @Test
    void streamsFirstZipFileEntry() throws IOException {
        Path file = Files.createTempFile("gclogsource-streams", ".zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(file))) {
            outputStream.putNextEntry(new ZipEntry("directory/"));
            outputStream.closeEntry();
            outputStream.putNextEntry(new ZipEntry("gc.log"));
            outputStream.write("zip line\n".getBytes());
            outputStream.closeEntry();
        }

        try (var lines = GCLogSourceStreams.lines(file, GCLogSourceFormat.ZIP)) {
            assertEquals(List.of("zip line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void streamsGzipLines() throws IOException {
        Path file = Files.createTempFile("gclogsource-streams", ".gz");
        try (GZIPOutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(file))) {
            outputStream.write("gzip line\n".getBytes());
        }

        try (var lines = GCLogSourceStreams.lines(file, GCLogSourceFormat.GZIP)) {
            assertEquals(List.of("gzip line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rejectsUnsupportedFormat() {
        assertThrows(IOException.class, () -> GCLogSourceStreams.lines(Path.of("directory"), GCLogSourceFormat.DIRECTORY));
    }
}
