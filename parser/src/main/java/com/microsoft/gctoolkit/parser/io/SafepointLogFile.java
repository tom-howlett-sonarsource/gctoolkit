// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.io.core.FileFormat;
import com.microsoft.gctoolkit.io.core.GCLogSources;
import com.microsoft.gctoolkit.jvm.Diary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final FileFormat fileFormat;
    private final Path path;

    public SafepointLogFile(Path path) {
        this.path = path;
        this.fileFormat = GCLogSources.detectFormat(path);
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
        if (fileFormat == FileFormat.PLAINTEXT) {
            return GCLogSources.streamPlainText(path);
        } else if (fileFormat == FileFormat.ZIP) {
            return GCLogSources.streamZipFile(path);
        } else if (fileFormat == FileFormat.GZIP) {
            return GCLogSources.streamGZipFile(path);
        }
        throw new IOException("Unable to read " + path.toString());
    }

}
