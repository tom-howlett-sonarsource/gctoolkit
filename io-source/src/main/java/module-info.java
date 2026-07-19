// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for GC log source discovery, byte sizing, and
 * opening plain, ZIP, and GZIP log streams. Used by both the API and
 * parser modules to avoid duplicated production IO behavior.
 */
module com.microsoft.gctoolkit.io.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.source;
}
