// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared IO utilities for GC log sources. Provides format detection,
 * byte sizing, and helpers for opening plain, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.logio {
    requires java.logging;

    exports com.microsoft.gctoolkit.logio;
}
