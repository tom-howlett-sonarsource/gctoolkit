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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleGCLogFileSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGZipSourcesConsistently() throws IOException {
        String content = " first \n\nsecond\n";
        Path plain = Files.writeString(temporaryDirectory.resolve("gc.log"), content);
        Path gzip = gzip(content);
        Path zip = zip(content);

        List<String> expected = List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL);
        assertEquals(expected, lines(plain));
        assertEquals(expected, lines(gzip));
        assertEquals(expected, lines(zip));
    }

    @Test
    void metadataRetainsFormatAndPhysicalSize() throws IOException {
        Path source = gzip("line\n");

        LogFileMetadata metadata = new SingleGCLogFile(source).getMetaData();

        assertTrue(metadata.isGZip());
        assertEquals(Files.size(source), metadata.getByteSize());
    }

    private List<String> lines(Path path) throws IOException {
        try (var stream = new SingleGCLogFile(path).stream()) {
            return stream.collect(Collectors.toList());
        }
    }

    private Path gzip(String content) throws IOException {
        Path source = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path zip(String content) throws IOException {
        Path source = temporaryDirectory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }
}
