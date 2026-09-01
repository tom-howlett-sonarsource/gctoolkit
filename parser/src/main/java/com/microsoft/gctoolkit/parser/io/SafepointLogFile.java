// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.jvm.Diary;
import com.microsoft.gctoolkit.source.LogSourceMetadata;
import com.microsoft.gctoolkit.source.LogSourceReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final Path path;
    private LogSourceMetadata metadata;

    public SafepointLogFile(Path path) {
        this.path = path;
    }

    /**
     * For the moment this diary is empty.
     * @return a diary
     */
    public Diary diary() {
        return new Diary();
    }

    @Override
    public String endOfData() {
        return GCLogFile.END_OF_DATA_SENTINEL;
    }

    public Path getPath() { return path; }

    public Stream<String> stream() throws IOException {
        return LogSourceReader.stream(metadata());
    }

    private LogSourceMetadata metadata() throws IOException {
        if (metadata == null) {
            metadata = new LogSourceMetadata(path);
        }
        return metadata;
    }

}
