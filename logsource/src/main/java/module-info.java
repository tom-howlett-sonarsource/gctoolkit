// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities used by the API and parser modules. The module provides
 * primitives for discovering the format of a GC log source, sizing/sniffing its leading bytes,
 * and opening plain text, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
