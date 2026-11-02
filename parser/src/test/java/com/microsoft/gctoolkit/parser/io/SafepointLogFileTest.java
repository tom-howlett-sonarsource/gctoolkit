// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafepointLogFileTest {

    @TempDir
    Path directory;

    @Test
    void zipStreamCloseReleasesReader() throws IOException {
        Path path = directory.resolve("safepoints.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("safepoints.log"));
            zip.write("safepoint\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        SafepointLogFile log = new SafepointLogFile(path);
        try (Stream<String> lines = log.streamZipFile()) {
            assertEquals("safepoint", lines.findFirst().orElseThrow());
        }
        Stream<String> lines = log.streamZipFile();
        Iterator<String> iterator = lines.iterator();
        lines.close();
        assertThrows(UncheckedIOException.class, iterator::hasNext);
    }

    @Test
    void emptyZipIsReadable() throws IOException {
        Path path = directory.resolve("empty.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.finish();
        }
        try (Stream<String> lines = new SafepointLogFile(path).streamZipFile()) {
            assertEquals(0, lines.count());
        }
    }
}
