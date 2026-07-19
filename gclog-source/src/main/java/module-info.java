// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for discovering GC log source formats,
 * measuring source size, and opening plain, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.gclog.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.gclog.source;
}
