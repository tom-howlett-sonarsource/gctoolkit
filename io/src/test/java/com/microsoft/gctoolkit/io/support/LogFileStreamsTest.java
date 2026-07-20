// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogFileStreamsTest {

    @Test
    void opensPlainText(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve("gc.log");
        Files.writeString(plain, "line1\nline2\n");
        try (Stream<String> stream = LogFileStreams.openPlainText(plain)) {
            assertEquals(List.of("line1", "line2"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void opensZipFirstEntry(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("gc.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("dir/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write("alpha\nbeta\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> stream = LogFileStreams.openZip(zip)) {
            assertEquals(List.of("alpha", "beta"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void opensGZip(@TempDir Path tmp) throws IOException {
        Path gz = tmp.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("gamma\ndelta\n".getBytes(StandardCharsets.UTF_8));
        }
        try (Stream<String> stream = LogFileStreams.openGZip(gz)) {
            assertEquals(List.of("gamma", "delta"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void gZipOpenerFailsFastOnPlainFile(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve("gc.log");
        Files.writeString(plain, "not a gzip");
        assertThrows(IOException.class, () -> LogFileStreams.openGZip(plain));
    }

    @Test
    void openersRejectNullPaths() {
        assertThrows(NullPointerException.class, () -> LogFileStreams.openPlainText(null));
        assertThrows(NullPointerException.class, () -> LogFileStreams.openZip(null));
        assertThrows(NullPointerException.class, () -> LogFileStreams.openGZip(null));
    }
}
