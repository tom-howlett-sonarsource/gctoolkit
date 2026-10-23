// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities used by both the API and parser modules.
 * Provides source discovery, byte sizing (magic byte sniffing), and opening of
 * plain, ZIP, and GZIP GC log streams.
 */
module com.microsoft.gctoolkit.io.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.source;
}
