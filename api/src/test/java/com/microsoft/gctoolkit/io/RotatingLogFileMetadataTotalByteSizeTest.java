// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSegmentsWhenConstructedFromAnIndividualMember() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path older = Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log"), current);

        assertEquals(first.length + current.length,
                new RotatingLogFileMetadata(older).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenFileFormatIsUnrecognized() throws Exception {
        Path gzip = directory.resolve("gc.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gc log content\n".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(0L, new RotatingLogFileMetadata(gzip).getTotalByteSize());
    }
}
