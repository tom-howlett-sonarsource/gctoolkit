// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for opening GC log sources — plain text, ZIP, and GZIP —
 * along with source discovery and byte-size helpers. Consumed by the api and
 * parser modules to eliminate duplicated production IO.
 */
module com.microsoft.gctoolkit.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.source;
}
