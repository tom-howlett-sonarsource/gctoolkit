// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.io.LogFileMetadata;
import com.microsoft.gctoolkit.jvm.Diary;
import com.microsoft.gctoolkit.logsource.LogSources;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final LogFileMetadata metadata = null;

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
        if (metadata.isPlainText()) {
            return LogSources.openPlainLines(path);
        } else if (metadata.isZip()) {
            return LogSources.openZipLines(path);
        } else if (metadata.isGZip()) {
            return LogSources.openGZipLines(path);
        }
        throw new IOException("Unable to read " + path.toString());
    }

    /**
     * Alternative to {@link #stream()} that discovers the file format directly
     * from disk rather than relying on {@link LogFileMetadata}.
     * @return a lazy line stream
     * @throws IOException when the file cannot be opened or its format is not readable
     */
    Stream<String> streamByFormat() throws IOException {
        return LogSources.openLines(path, LogSources.detectFormat(path));
    }

    Stream<String> streamZipFile() throws IOException {
        return LogSources.openZipLines(path);
    }

    Stream<String> streamGZipFile() throws IOException {
        return LogSources.openGZipLines(path);
    }

}
