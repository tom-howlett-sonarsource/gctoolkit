// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production utilities for GC log sources: format detection, byte sizing,
 * source discovery, and opening plain / ZIP / GZIP line streams.
 */
module com.microsoft.gctoolkit.logio {
    requires java.logging;

    exports com.microsoft.gctoolkit.logio;
}
