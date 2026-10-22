// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for discovering, sizing and opening GC log sources. This is an internal module.
 */
module com.microsoft.gctoolkit.logsource {
    exports com.microsoft.gctoolkit.logsource to
            com.microsoft.gctoolkit.api,
            com.microsoft.gctoolkit.parser;
}
