// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities used by both the API and parser modules. Provides
 * source discovery, byte sizing, and the opening of plain, ZIP, and GZIP log streams.
 */
module com.microsoft.gctoolkit.logfile {
    requires java.logging;

    exports com.microsoft.gctoolkit.logfile;
}
