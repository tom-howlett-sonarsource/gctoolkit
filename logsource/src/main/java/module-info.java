// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the utilities used to discover, size and read the log sources consumed by the
 * Microsoft, Java Garbage Collection Toolkit. The utilities are shared by the API and the
 * parser modules.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
