// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production utilities for opening GC log sources (plain, ZIP, GZIP)
 * and inspecting their format and byte size.
 */
module com.microsoft.gctoolkit.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.source;
}
