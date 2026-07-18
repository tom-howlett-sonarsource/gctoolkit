// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

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

class SafepointLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGZipSources() throws IOException {
        String content = "first\nsecond\n";
        Path plain = Files.writeString(temporaryDirectory.resolve("safepoint.log"), content);
        Path gzip = gzip(content);
        Path zip = zip(content);

        List<String> expected = List.of("first", "second");
        assertEquals(expected, lines(plain));
        assertEquals(expected, lines(gzip));
        assertEquals(expected, lines(zip));
    }

    private List<String> lines(Path path) throws IOException {
        try (var stream = new SafepointLogFile(path).stream()) {
            return stream.collect(Collectors.toList());
        }
    }

    private Path gzip(String content) throws IOException {
        Path source = temporaryDirectory.resolve("safepoint.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path zip(String content) throws IOException {
        Path source = temporaryDirectory.resolve("safepoint.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/safepoint.log"));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }
}
