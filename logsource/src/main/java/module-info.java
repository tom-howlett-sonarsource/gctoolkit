// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for discovering, sizing and opening GC log sources.
 * This is an internal module used by the GCToolKit API and parser modules.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource to
            com.microsoft.gctoolkit.api,
            com.microsoft.gctoolkit.parser;
}
