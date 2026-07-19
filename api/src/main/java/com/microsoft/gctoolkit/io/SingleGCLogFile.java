// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.gclog.source.GCLogSources;
import com.microsoft.gctoolkit.gclog.source.SourceFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A single GC log file. If the file is a zip or gzip file,
 * then the first entry is the file of interest.
 */
public class SingleGCLogFile extends GCLogFile {

    private SingleLogFileMetadata metadata = null;

    /**
     * Constructor for a single, GC log file.
     * @param path The path to the log file.
     */
    public SingleGCLogFile(Path path) {
        super(path);
    }

    @Override
    public LogFileMetadata getMetaData() throws IOException {
        if (metadata == null) {
            metadata = new SingleLogFileMetadata(path);
        }
        return metadata;
    }

    @Override
    public Stream<String> stream() throws IOException {
        LogFileMetadata md = getMetaData();
        SourceFormat format = md.sourceFormat();
        if (format != SourceFormat.PLAINTEXT && format != SourceFormat.ZIP && format != SourceFormat.GZIP) {
            throw new IOException("Unable to read " + path);
        }
        Stream<String> stream = GCLogSources.openLines(md.getPath(), format);
        return Stream.concat(stream
                        .filter(Objects::nonNull)
                        .filter(line -> !line.isBlank())
                        .map(String::trim)
                        .filter(s -> !s.isEmpty()),
                Stream.of(endOfData()));
    }

}
