// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Contains the file system utilities shared by the modules that read GC log sources.
 * <p>
 * The utilities cover the behavior that is common to every GC log source: discovering the
 * format of a source, reporting its size in bytes, and opening a stream of log lines from a
 * plain text, ZIP, or GZIP source.
 */
module com.microsoft.gctoolkit.logsource {
    requires java.logging;

    exports com.microsoft.gctoolkit.logsource;
}
