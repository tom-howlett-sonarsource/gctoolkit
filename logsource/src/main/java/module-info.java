// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the utilities shared by the GCToolKit API and parser modules for working with
 * GC log sources held in a file system: discovering the kind and contents of a source,
 * sizing it, and opening plain text, ZIP, and GZIP sources as a stream of log lines.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
