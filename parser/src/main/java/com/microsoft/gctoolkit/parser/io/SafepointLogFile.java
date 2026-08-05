// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.jvm.Diary;
import com.microsoft.gctoolkit.shared.io.LogFileSource;

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
        LogFileSource source = LogFileSource.from(path);
        if (source.format() == LogFileSource.Format.ZIP) {
            return streamZipFile();
        } else if (source.format() == LogFileSource.Format.GZIP) {
            return streamGZipFile();
        } else if (source.format() == LogFileSource.Format.PLAIN_TEXT) {
            return source.lines();
        }
        throw new IOException("Unable to read " + path.toString());
    }

    Stream<String> streamZipFile() throws IOException {
        return LogFileSource.from(path).lines();
    }

    Stream<String> streamGZipFile() throws IOException {
        return LogFileSource.from(path).lines();
    }

}
