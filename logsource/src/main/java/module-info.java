// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the utilities used to discover, size and read GC log sources. These utilities are
 * shared by the API and the parser.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
