// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source IO utilities used by the API and parser modules.
 * Provides format detection, byte sizing, and stream opening for plain, ZIP, and GZIP log files.
 */
module com.microsoft.gctoolkit.io.core {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.core;
}
