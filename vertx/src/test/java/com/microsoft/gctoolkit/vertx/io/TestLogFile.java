// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.vertx.io;

import com.microsoft.gctoolkit.shared.io.GCLogSource;

import java.io.File;

/**
 * Abstract the log files location and provide some parsing of the file name scheme.
 */
public class TestLogFile extends GCLogSource {

    public TestLogFile(String fileName) {
        super(fileName);
    }

    public TestLogFile(File file) {
        super(file);
    }
}
