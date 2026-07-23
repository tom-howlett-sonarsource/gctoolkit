// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities for format detection and stream opening,
 * used by both the API and parser modules.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
