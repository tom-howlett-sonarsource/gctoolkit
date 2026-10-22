// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities used by the API and parser modules. Provides
 * source discovery, byte sizing, and opening of plain, ZIP and GZIP log streams.
 */
module com.microsoft.gctoolkit.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.source;
}
