// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the utilities used to discover, size and read the log sources that GCToolKit analyses.
 * The utilities are shared by the API and the parser so that both read plain text, Zip and GZip
 * logs in exactly the same way.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
