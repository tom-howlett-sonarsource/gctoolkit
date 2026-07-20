// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for locating GC log sources, detecting file format from
 * magic bytes, and opening plain, ZIP, and GZIP log streams. Consumed by both
 * the API and parser modules.
 */
module com.microsoft.gctoolkit.io.support {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.support;
}
