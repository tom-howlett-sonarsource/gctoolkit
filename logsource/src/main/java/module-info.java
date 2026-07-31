// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for locating and reading the file system sources that hold GC logs.
 * <p>
 * This module is used by both the GCToolKit API and the GCToolKit parser so that the
 * rules for discovering the format of a log source, sizing it, and opening plain text,
 * ZIP and GZIP log streams live in exactly one place.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
