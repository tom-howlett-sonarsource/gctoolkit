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
    Path directory;

    @Test
    void streamsPlainGzipAndZipSources() throws IOException {
        Path plain = Files.writeString(directory.resolve("plain.log"), "plain\n");
        Path gzip = gzip("gzip.log", "gzip\n");
        Path zip = zip("zip.log", "folder/", null, "folder/safepoint.log", "zip\n");

        assertEquals(List.of("plain"), lines(plain));
        assertEquals(List.of("gzip"), lines(gzip));
        assertEquals(List.of("zip"), lines(zip));
    }

    private List<String> lines(Path path) throws IOException {
        try (var lines = new SafepointLogFile(path).stream()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path gzip(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String name, String... entries) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (int index = 0; index < entries.length; index += 2) {
                output.putNextEntry(new ZipEntry(entries[index]));
                if (entries[index + 1] != null) {
                    output.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return path;
    }
}
