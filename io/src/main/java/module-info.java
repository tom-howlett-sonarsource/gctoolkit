// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities for format detection and stream opening.
 * Used by both the API and parser modules.
 */
module com.microsoft.gctoolkit.io {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
