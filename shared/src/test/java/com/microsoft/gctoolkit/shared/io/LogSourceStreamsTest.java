// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceStreamsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void streamsPlainTextGzipAndZipSources() throws IOException {
        Path plainText = tempDirectory.resolve("gc.log");
        Files.write(plainText, List.of("a", "b"));

        Path gzip = tempDirectory.resolve("gc.log.gz");
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            gzipOutputStream.write("c\nd\n".getBytes());
        }

        Path zip = tempDirectory.resolve("gc.log.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zip))) {
            zipOutputStream.putNextEntry(new ZipEntry("directory/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("gc.log"));
            zipOutputStream.write("e\nf\n".getBytes());
            zipOutputStream.closeEntry();
        }

        assertEquals(
                List.of("a", "b"),
                LogSourceStreams.stream(plainText, LogSourceFormat.PLAINTEXT).collect(Collectors.toList()));
        assertEquals(
                List.of("c", "d"),
                LogSourceStreams.stream(gzip, LogSourceFormat.GZIP).collect(Collectors.toList()));
        assertEquals(
                List.of("e", "f"),
                LogSourceStreams.stream(zip, LogSourceFormat.ZIP).collect(Collectors.toList()));
    }
}
