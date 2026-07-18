// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.gclogsource.LogSourceFormat;
import com.microsoft.gctoolkit.gclogsource.LogSourceIO;
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
        LogSourceFormat format = LogSourceIO.detectFormat(path);
        if (format == LogSourceFormat.PLAINTEXT || format == LogSourceFormat.ZIP || format == LogSourceFormat.GZIP) {
            return LogSourceIO.stream(path, format);
        }
        throw new IOException("Unable to read " + path.toString());
    }

}
