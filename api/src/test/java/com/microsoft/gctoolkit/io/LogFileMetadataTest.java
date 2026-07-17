// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileMetadataTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsSourceSizeUsingSharedUtility() throws IOException {
        Path log = Files.writeString(temporaryDirectory.resolve("gc.log"), "12345678");

        assertEquals(8L, new SingleLogFileMetadata(log).getSize());
    }
}
