// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.gclogsource.GCLogFileFormat;
import com.microsoft.gctoolkit.gclogsource.GCLogSources;
import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
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
        GCLogFileFormat format = GCLogSources.formatOf(path);
        if (format == GCLogFileFormat.PLAIN_TEXT) {
            return GCLogSources.lines(path);
        } else if (format == GCLogFileFormat.ZIP) {
            return streamZipFile();
        } else if (format == GCLogFileFormat.GZIP) {
            return streamGZipFile();
        }
        throw new IOException("Unable to read " + path.toString());
    }

    Stream<String> streamZipFile() throws IOException {
        return GCLogSources.lines(path);
    }

    Stream<String> streamGZipFile() throws IOException {
        return GCLogSources.lines(path);
    }

}
