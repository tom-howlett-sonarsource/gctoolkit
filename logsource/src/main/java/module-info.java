// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the utilities shared by GCToolKit modules that need to locate, size and read
 * GC log sources. A source may be a plain text file, a ZIP or GZIP compressed file, or a
 * directory holding the segments of a rotating log.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
