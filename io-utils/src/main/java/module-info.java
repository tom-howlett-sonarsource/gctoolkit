// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for locating GC log sources, sizing them in bytes, and
 * opening line streams from plain text, ZIP, and GZIP log files.
 */
module com.microsoft.gctoolkit.io.utils {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.util;
}
