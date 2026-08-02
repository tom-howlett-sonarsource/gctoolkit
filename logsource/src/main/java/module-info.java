// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Utilities shared by the API and parser modules for working with GC log sources. The module
 * knows how to discover the format of a log source, report its size in bytes, and open a stream
 * of lines over plain text, ZIP, and GZIP sources.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
