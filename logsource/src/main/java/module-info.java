// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for discovering, sizing and opening GC log sources.
 * Used internally by the GCToolKit API and parser modules.
 */
module com.microsoft.gctoolkit.logsource {
    exports com.microsoft.gctoolkit.logsource to
            com.microsoft.gctoolkit.api,
            com.microsoft.gctoolkit.parser;
}
