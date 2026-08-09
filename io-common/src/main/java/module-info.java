// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for discovering GC log source format and opening
 * plain, ZIP, and GZIP log streams. Consumed by the gctoolkit-api and
 * gctoolkit-parser modules.
 */
module com.microsoft.gctoolkit.io.common {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.common;
}
