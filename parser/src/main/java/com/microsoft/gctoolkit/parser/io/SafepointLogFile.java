// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.io.support.GCLogSources;
import com.microsoft.gctoolkit.jvm.Diary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final Path path;

    public SafepointLogFile(Path path) {
        this.path = path;
    }

    /**
     * Safepoint logs do not currently contribute diary entries, so the returned
     * {@link Diary} is intentionally empty.
     *
     * @return an empty diary
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
        return GCLogSources.openLines(path);
    }

}
