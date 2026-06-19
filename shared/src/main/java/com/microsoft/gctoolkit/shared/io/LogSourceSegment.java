// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A readable segment of a GC log source.
 */
public interface LogSourceSegment {

    Path getPath();
    String getSegmentName();
    double getStartTime();
    double getEndTime();
    Stream<String> stream();
}
