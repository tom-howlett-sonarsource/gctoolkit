// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.jvm.Diary;
import com.microsoft.gctoolkit.shared.io.LogSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final Path path;

    public SafepointLogFile(Path path) {
        this.path = path;
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
        return LogSource.discover(path).lines();
    }

    Stream<String> streamZipFile() throws IOException {
        LogSource source = LogSource.discover(path);
        if (!source.isZip()) {
            throw new IOException(path + " is not a ZIP source");
        }
        return source.lines();
    }

    Stream<String> streamGZipFile() throws IOException {
        LogSource source = LogSource.discover(path);
        if (!source.isGZip()) {
            throw new IOException(path + " is not a GZIP source");
        }
        return source.lines();
    }

}
