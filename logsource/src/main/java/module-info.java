// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared, production IO utilities used by API and parser modules
 * to discover GC log sources and open plain, ZIP and GZIP streams.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;
    exports com.microsoft.gctoolkit.logsource;
}
