// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogFileFormatTest {

    @Test
    void enumHasExpectedValues() {
        assertEquals(5, LogFileFormat.values().length);
        assertNotNull(LogFileFormat.valueOf("PLAINTEXT"));
        assertNotNull(LogFileFormat.valueOf("ZIP"));
        assertNotNull(LogFileFormat.valueOf("GZIP"));
        assertNotNull(LogFileFormat.valueOf("DIRECTORY"));
        assertNotNull(LogFileFormat.valueOf("UNKNOWN"));
    }
}
