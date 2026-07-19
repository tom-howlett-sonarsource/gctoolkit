// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared file-source utilities used by both the API and parser modules to
 * discover a GC log's underlying storage format and open a line stream over
 * plain, ZIP, and GZIP files.
 */
module com.microsoft.gctoolkit.io {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.source;
}
