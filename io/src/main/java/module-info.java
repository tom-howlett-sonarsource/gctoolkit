// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities: format detection, byte sizing, and
 * opening plain, ZIP, and GZIP log streams. Consumed by both the API and
 * parser modules.
 */
module com.microsoft.gctoolkit.io.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.source;
}
