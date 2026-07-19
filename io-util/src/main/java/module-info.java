// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source discovery, magic-byte sizing, and stream-opening utilities.
 * Used by both the API and parser modules to avoid duplicating production IO code.
 */
module com.microsoft.gctoolkit.io.util {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.util;
}
