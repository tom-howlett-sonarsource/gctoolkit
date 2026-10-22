// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for discovering, sizing and opening GC log sources.
 */
module com.microsoft.gctoolkit.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.source to
            com.microsoft.gctoolkit.api,
            com.microsoft.gctoolkit.parser;
}
