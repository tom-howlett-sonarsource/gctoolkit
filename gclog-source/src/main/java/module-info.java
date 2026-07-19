// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production utilities for GC log sources: format discovery, byte sizing,
 * and opening plain, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.gclog.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.gclog.source;
}
