// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities (format discovery, magic-byte sizing and stream opening)
 * used by both the GCToolKit API and parser modules.
 */
module com.microsoft.gctoolkit.io.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.source;
}
