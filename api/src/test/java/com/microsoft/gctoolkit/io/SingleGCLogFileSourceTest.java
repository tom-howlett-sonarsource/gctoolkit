// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

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

class SingleGCLogFileSourceTest {

    @TempDir
    Path directory;

    @Test
    void streamsPlainGzipAndZipSourcesWithExistingFiltering() throws IOException {
        Path plain = write("plain.log", " first \n\nsecond\n");
        Path gzip = gzip("gzip.log", " first \n\nsecond\n");
        Path zip = zip("zip.log", "folder/", null, "folder/gc.log", " first \n\nsecond\n");

        assertEquals(expected(), lines(plain));
        assertEquals(expected(), lines(gzip));
        assertEquals(expected(), lines(zip));
    }

    private List<String> lines(Path path) throws IOException {
        try (var lines = new SingleGCLogFile(path).stream()) {
            return lines.collect(Collectors.toList());
        }
    }

    private List<String> expected() {
        return List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL);
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
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
