// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities. This module contains the common log discovery,
 * sizing, and streaming code used by both the API and parser modules so the logic
 * lives in a single place.
 */
module com.microsoft.gctoolkit.shared.io {
    requires java.logging;

    exports com.microsoft.gctoolkit.logfile;
}
