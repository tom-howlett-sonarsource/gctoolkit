// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.jvm.Diary;
import com.microsoft.gctoolkit.shared.io.GCLogSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final Path path;
    private GCLogSource source;

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
        GCLogSource logSource = source();
        if (logSource.getFormat() == GCLogSource.Format.PLAIN_TEXT
                || logSource.getFormat() == GCLogSource.Format.ZIP
                || logSource.getFormat() == GCLogSource.Format.GZIP) {
            return logSource.lines();
        }
        throw new IOException("Unable to read " + path.toString());
    }

    private GCLogSource source() throws IOException {
        if (source == null) {
            source = GCLogSource.discover(path);
        }
        return source;
    }

}
