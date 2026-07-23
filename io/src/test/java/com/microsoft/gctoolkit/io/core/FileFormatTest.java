// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileFormatTest {

    @Test
    void enumContainsExpectedValues() {
        FileFormat[] values = FileFormat.values();
        assertEquals(5, values.length);
        assertEquals(FileFormat.ZIP, FileFormat.valueOf("ZIP"));
        assertEquals(FileFormat.GZIP, FileFormat.valueOf("GZIP"));
        assertEquals(FileFormat.PLAINTEXT, FileFormat.valueOf("PLAINTEXT"));
        assertEquals(FileFormat.DIRECTORY, FileFormat.valueOf("DIRECTORY"));
        assertEquals(FileFormat.UNKNOWN, FileFormat.valueOf("UNKNOWN"));
    }
}
