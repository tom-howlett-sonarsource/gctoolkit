// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared production utilities for discovering and opening GC log data sources.
 * <p>
 * The module exposes format probing for GC log files (plain, ZIP, GZIP, directory)
 * along with helpers that open a line {@link java.util.stream.Stream} over such
 * files. It is used by both {@code com.microsoft.gctoolkit.api} and
 * {@code com.microsoft.gctoolkit.parser} so the IO behaviour is defined in a
 * single place.
 */
module com.microsoft.gctoolkit.sources {
    requires java.logging;

    exports com.microsoft.gctoolkit.sources.io;
}
