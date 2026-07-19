// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production utilities for detecting a GC log's source format
 * and opening plain, ZIP and GZIP log streams.
 */
module com.microsoft.gctoolkit.logio {
    requires java.logging;

    exports com.microsoft.gctoolkit.logio;
}
