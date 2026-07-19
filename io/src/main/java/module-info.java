// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared GC log source I/O utilities used by the API and parser modules.
 * <p>
 * The module exposes format detection (magic-byte sniffing), byte sizing, and
 * line-stream opening for plain-text, ZIP and GZIP GC log files. Placing this
 * behavior in its own module removes the duplication that previously existed
 * between {@code com.microsoft.gctoolkit.api} and
 * {@code com.microsoft.gctoolkit.parser}.
 */
module com.microsoft.gctoolkit.io.source {
    requires java.logging;

    exports com.microsoft.gctoolkit.io.source;
}
