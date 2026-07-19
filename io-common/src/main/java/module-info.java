// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for locating, sizing, and opening GC log sources
 * (plain-text, ZIP, and GZIP). Used by the API and parser modules.
 */
module com.microsoft.gctoolkit.io.common {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.common;
}
