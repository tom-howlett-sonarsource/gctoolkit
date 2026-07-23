// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities for format detection, byte sizing,
 * source discovery, and opening plain-text, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.io.core {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.core;
}
