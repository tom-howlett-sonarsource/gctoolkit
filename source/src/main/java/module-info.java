// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for GC log sources: source/format discovery, byte sizing,
 * and opening plain, ZIP, and GZIP log streams. Used by both the API and
 * parser modules to avoid duplicating the low-level IO behavior.
 */
module com.microsoft.gctoolkit.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.source;
}
