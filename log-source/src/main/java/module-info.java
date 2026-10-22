// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities used by both the API and parser modules.
 * <p>
 * Provides source discovery (detecting whether a path is a directory or a plain,
 * ZIP, or GZIP log), byte sizing (reading the magic bytes of a file), and opening
 * line streams over plain, ZIP, and GZIP GC log sources.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
