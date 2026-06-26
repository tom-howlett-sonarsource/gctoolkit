// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/*
 * Shared, dependency-free utilities for reading GC log files from the file
 * system. The toolkit stores GC logs as plain text, GZip, or Zip archives, and
 * the same line-streaming and "tail" (sizing) logic is needed by more than one
 * module. This module is the single home for that logic so callers do not
 * re-implement it.
 */
module com.microsoft.gctoolkit.io.source {
    exports com.microsoft.gctoolkit.io.source;
}
