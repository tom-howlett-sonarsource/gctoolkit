// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The recognised on-disk formats for a GC log source. Determined by inspecting
 * the leading bytes of the file, or {@link #DIRECTORY} when the source is a
 * directory of rotating log segments.
 */
public enum LogSourceFormat {
    PLAIN_TEXT,
    ZIP,
    GZIP,
    DIRECTORY,
    UNKNOWN
}
