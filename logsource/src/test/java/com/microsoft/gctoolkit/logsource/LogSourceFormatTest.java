// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceFormatTest {

    @TempDir
    Path directory;

    @Test
    void recognisesAnUncompressedSource() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceFormat.of(LogSources.plainText(directory, "gc.log", "line")));
    }

    @Test
    void recognisesAGZipCompressedSource() throws IOException {
        assertEquals(LogSourceFormat.GZIP, LogSourceFormat.of(LogSources.gzip(directory, "gc.log.gz", "line")));
    }

    @Test
    void recognisesAZipCompressedSource() throws IOException {
        assertEquals(LogSourceFormat.ZIP, LogSourceFormat.of(LogSources.zip(directory, "gc.zip", "gc.log")));
    }

    @Test
    void recognisesADirectory() {
        assertEquals(LogSourceFormat.DIRECTORY, LogSourceFormat.of(directory));
    }

    @Test
    void aSourceTooShortToHoldMagicBytesIsUncompressed() throws IOException {
        Path empty = Files.createFile(directory.resolve("empty.log"));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceFormat.of(empty));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceFormat.of(LogSources.plainText(directory, "tiny.log", "x")));
    }

    @Test
    void aNullPathHasNoKnownFormat() {
        assertEquals(LogSourceFormat.UNKNOWN, LogSourceFormat.of(null));
    }
}
