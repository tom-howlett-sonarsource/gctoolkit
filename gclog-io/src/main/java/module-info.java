// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities used by the API and parser modules to
 * discover log file formats, size log inputs in bytes, and open line
 * streams over plain-text, ZIP, and GZIP log files.
 */
module com.microsoft.gctoolkit.gclog.io {
    requires java.logging;

    exports com.microsoft.gctoolkit.gclog.io;
}
