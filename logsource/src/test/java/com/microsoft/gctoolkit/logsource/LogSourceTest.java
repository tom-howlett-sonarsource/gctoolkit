// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {
    private static final byte[] CONTENT = "gc log\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws Exception {
        verify(writePlain(), SourceFormat.PLAIN);
        verify(writeGzip(), SourceFormat.GZIP);
        verify(writeZip(), SourceFormat.ZIP);
        assertEquals(SourceFormat.DIRECTORY, LogSource.discover(directory));
    }

    private void verify(Path path, SourceFormat format) throws Exception {
        assertEquals(format, LogSource.discover(path));
        assertEquals(CONTENT.length, LogSource.byteSize(path));
        try (var input = LogSource.open(path)) {
            assertArrayEquals(CONTENT, input.readAllBytes());
        }
    }

    private Path writePlain() throws Exception {
        return Files.write(directory.resolve("gc.log"), CONTENT);
    }

    private Path writeGzip() throws Exception {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT);
        }
        return path;
    }

    private Path writeZip() throws Exception {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
        }
        return path;
    }
}
