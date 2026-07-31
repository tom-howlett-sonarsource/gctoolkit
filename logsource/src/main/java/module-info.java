// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the utilities used to discover, size and open the log sources that GCToolKit reads.
 * The module is shared by the API and the parser so that both read log files in exactly the same way.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
