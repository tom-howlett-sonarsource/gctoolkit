// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production utilities for discovering GC log source formats, sizing
 * their bytes on disk, and opening plain text, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.io.support {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.support;
}
