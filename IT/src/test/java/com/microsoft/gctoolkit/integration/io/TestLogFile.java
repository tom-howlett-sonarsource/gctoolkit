package com.microsoft.gctoolkit.integration.io;

import com.microsoft.gctoolkit.shared.io.GCLogSource;

import java.io.File;

public class TestLogFile extends GCLogSource {

    public TestLogFile(String fileName) {
        super(fileName);
    }

    public TestLogFile(File file) {
        super(file);
    }
}
