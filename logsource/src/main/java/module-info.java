// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared, file system level utilities for working with GC log sources. The module knows how to
 * discover log sources, how big they are and how to open them for reading, but nothing about the
 * content of a GC log. It is used by both the GCToolKit API and the GCToolKit parser.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
