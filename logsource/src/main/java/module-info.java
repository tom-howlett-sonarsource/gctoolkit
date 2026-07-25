// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the IO utilities used to locate, size and open the GC log sources that GCToolKit
 * analyses. The utilities are shared by the API and the parser modules.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
