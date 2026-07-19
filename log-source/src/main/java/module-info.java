// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production utilities for GC log source discovery, byte sizing,
 * and opening plain, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.log.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.log.source;
}
