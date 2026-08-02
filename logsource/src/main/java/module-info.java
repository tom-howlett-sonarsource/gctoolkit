// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the log source utilities shared by the GCToolKit API and parser modules. The utilities
 * cover the discovery of log sources, the sizing of those sources, and the opening of plain text,
 * ZIP and GZIP encoded logs.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
