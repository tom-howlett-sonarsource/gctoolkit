// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Internal utilities for discovering, sizing and opening GC log sources.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource to
            com.microsoft.gctoolkit.api,
            com.microsoft.gctoolkit.parser;
}
