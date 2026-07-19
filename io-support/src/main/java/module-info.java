// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production IO utilities for GC log source discovery, byte sizing,
 * and opening plain, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.io.support {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.support;
}
