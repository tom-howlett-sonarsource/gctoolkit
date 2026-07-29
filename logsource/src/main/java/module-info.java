// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the GC log source utilities that are shared by the GCToolKit API and parser modules.
 * The utilities discover the format of a log source, report its size in bytes, and open line
 * streams over plain text, ZIP, and GZIP logs.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
