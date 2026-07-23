// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.jvm.Diary;
import com.microsoft.gctoolkit.logsource.FileFormat;
import com.microsoft.gctoolkit.logsource.LogSourceStreams;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final Path path;
    private final FileFormat format;

    public SafepointLogFile(Path path) {
        this.path = path;
        this.format = FileFormat.detect(path);
    }

    /**
     * todo: for the moment this diary is empty.
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
        switch (format) {
            case PLAINTEXT:
                return LogSourceStreams.plainTextLines(path);
            case ZIP:
                return LogSourceStreams.zipLines(path);
            case GZIP:
                return LogSourceStreams.gzipLines(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }

}
