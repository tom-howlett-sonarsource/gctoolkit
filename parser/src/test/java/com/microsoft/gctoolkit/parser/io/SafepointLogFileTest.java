// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.GCLogFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafepointLogFileTest {

    private static final List<String> LINES = List.of(
            "0.293: RevokeBias                       [      12          0              0    ]      [     0     0     0     0     0    ]  0",
            "0.295: no vm operation                  [      12          1              1    ]      [     0     0     0     0     0    ]  0");

    @TempDir
    Path directory;

    @Test
    void streamsAPlainTextSafepointLog() throws IOException {
        Path path = Files.write(directory.resolve("safepoint.log"), LINES, UTF_8);
        assertEquals(LINES, readLines(new SafepointLogFile(path)));
    }

    @Test
    void streamsAGZipCompressedSafepointLog() throws IOException {
        Path path = directory.resolve("safepoint.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(LINES.stream().collect(Collectors.joining("\n", "", "\n")).getBytes(UTF_8));
        }
        assertEquals(LINES, readLines(new SafepointLogFile(path)));
    }

    @Test
    void rejectsASourceThatCannotBeRead() {
        SafepointLogFile logFile = new SafepointLogFile(directory);
        assertThrows(IOException.class, logFile::stream);
    }

    @Test
    void reportsTheEndOfDataSentinelAndItsPath() {
        Path path = directory.resolve("safepoint.log");
        SafepointLogFile logFile = new SafepointLogFile(path);
        assertEquals(GCLogFile.END_OF_DATA_SENTINEL, logFile.endOfData());
        assertEquals(path, logFile.getPath());
    }

    private List<String> readLines(SafepointLogFile logFile) throws IOException {
        try (Stream<String> lines = logFile.stream()) {
            return lines.collect(Collectors.toList());
        }
    }
}
