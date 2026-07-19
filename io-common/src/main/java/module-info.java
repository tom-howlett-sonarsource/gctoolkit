// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for GC log sources: source format discovery, byte sizing,
 * and stream opening for plain, ZIP, and GZIP encoded log files.
 */
module com.microsoft.gctoolkit.io.common {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.common;
}
