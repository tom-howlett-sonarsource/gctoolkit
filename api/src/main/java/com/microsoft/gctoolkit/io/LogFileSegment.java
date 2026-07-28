package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public interface LogFileSegment {

    String ROTATING_LOG_SUFFIX = ".*\\.(\\d+)(\\.current)?$";
    Pattern ROTATING_LOG_PATTERN = Pattern.compile(ROTATING_LOG_SUFFIX);

    Path getPath();
    String getSegmentName();
    double getStartTime();
    double getEndTime();

    /**
     * Return the byte size of this log file segment.
     *
     * @return the byte size
     * @throws IOException when the segment size cannot be read
     */
    long getByteSize() throws IOException;
    Stream<String> stream();
}
