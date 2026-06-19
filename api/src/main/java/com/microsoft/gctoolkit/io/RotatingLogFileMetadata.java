// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.shared.io.RotatingLogSourceDiscovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public class RotatingLogFileMetadata extends LogFileMetadata {

    private List<LogFileSegment> segments;

    public RotatingLogFileMetadata(Path path) throws IOException {
        super(path);
    }

    public Stream<LogFileSegment> logFiles() {
        if ( segments == null) {
            segments = RotatingLogSourceDiscovery.discover(
                    getPath(),
                    getFormat(),
                    GCLogFileSegment::new,
                    GCLogFileZipSegment::new);
        }
        return segments.stream();
    }

    /**
     * Return the number of files. Useful if the file is a compressed file which may
     * contain multiple entries.
     * @return The number of files in the file.
     */
    public int getNumberOfFiles() {
        if ( this.segments == null)
            segments = RotatingLogSourceDiscovery.discover(
                    getPath(),
                    getFormat(),
                    GCLogFileSegment::new,
                    GCLogFileZipSegment::new);
        return this.segments.size();
    }
}
