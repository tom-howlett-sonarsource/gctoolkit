// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared, low-level utilities for discovering GC log sources on disk and opening
 * plain-text, ZIP, or GZIP compressed log streams. This module is consumed by
 * both {@code com.microsoft.gctoolkit.api} and {@code com.microsoft.gctoolkit.parser}
 * to avoid duplicating byte-magic detection and stream-opening code.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
