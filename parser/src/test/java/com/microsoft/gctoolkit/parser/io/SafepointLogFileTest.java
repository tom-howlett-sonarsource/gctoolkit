// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SafepointLogFileTest {

    private static final List<String> LINES = List.of("first", "second");

    @TempDir
    private Path tempDirectory;

    @Test
    void streamsPlainTextSource() throws IOException {
        Path path = Files.write(tempDirectory.resolve("safepoint.log"), LINES);

        assertEquals(LINES, stream(path));
    }

    @Test
    void streamsGZipSource() throws IOException {
        Path path = tempDirectory.resolve("safepoint.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content());
        }

        assertEquals(LINES, stream(path));
    }

    @Test
    void streamsZipSource() throws IOException {
        Path path = tempDirectory.resolve("safepoint.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write(content());
            output.closeEntry();
        }

        assertEquals(LINES, stream(path));
    }

    private List<String> stream(Path path) throws IOException {
        SafepointLogFile source = new SafepointLogFile(path);
        assertEquals(path, source.getPath());
        assertEquals("END_OF_DATA_SENTINEL", source.endOfData());
        assertNotNull(source.diary());
        try (Stream<String> lines = source.stream()) {
            return lines.collect(Collectors.toList());
        }
    }

    private byte[] content() {
        return (String.join("\n", LINES) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
