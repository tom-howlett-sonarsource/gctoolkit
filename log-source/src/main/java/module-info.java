// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities used by both the API and parser modules.
 * Provides source-format discovery, byte sizing, and line streams over plain,
 * ZIP, and GZIP GC log files.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
