// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared, low level utilities used to discover, size and open GC log sources.
 * The module is used by both the GCToolKit API and parser modules so that the
 * handling of plain text, ZIP and GZIP log files is defined in exactly one place.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
