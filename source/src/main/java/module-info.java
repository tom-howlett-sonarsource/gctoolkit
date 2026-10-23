// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for discovering, sizing, and opening GC log sources.
 * Used by both the API and parser modules to avoid duplicating the production
 * IO behaviour for plain, ZIP, and GZIP log files.
 */
module com.microsoft.gctoolkit.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.source;
}
