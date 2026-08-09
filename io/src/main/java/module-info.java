// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities used by both the API and parser modules.
 * Provides file-format detection, byte sizing, and line-stream open helpers
 * for plain, ZIP, and GZIP GC log files.
 */
module com.microsoft.gctoolkit.io.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.source;
}
