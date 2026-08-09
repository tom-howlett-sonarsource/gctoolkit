// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production IO helpers for GC log source discovery and stream opening.
 * Used by both the API and parser modules.
 */
module com.microsoft.gctoolkit.io.support {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.support;
}
