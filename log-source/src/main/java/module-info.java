// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source utilities: format discovery, byte sizing, and
 * plain/ZIP/GZIP stream opening. Used by both the API and parser modules to
 * avoid duplicating IO behavior.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
