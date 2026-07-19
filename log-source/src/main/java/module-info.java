// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for discovering GC log sources, detecting their on-disk
 * format from magic bytes, and opening line streams over plain, ZIP, and
 * GZIP log files.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
