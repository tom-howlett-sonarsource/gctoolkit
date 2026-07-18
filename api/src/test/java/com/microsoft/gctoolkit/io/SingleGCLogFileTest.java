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
import java.util.zip.GZIPOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleGCLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsCompressedLogThroughSharedSource() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(" first \n\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        SingleGCLogFile logFile = new SingleGCLogFile(path);

        assertEquals(Files.size(path), logFile.getMetaData().getByteSize());
        try (var lines = logFile.stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
    }
}
