// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * IO utilities shared by the modules that read GC log sources. A log source is a file, a compressed
 * file, or a directory holding one or more GC log files. {@link com.microsoft.gctoolkit.logsource.GCLogSource}
 * discovers the {@link com.microsoft.gctoolkit.logsource.LogFileFormat} of a source, reports its size,
 * and opens it for reading.
 */
package com.microsoft.gctoolkit.logsource;
