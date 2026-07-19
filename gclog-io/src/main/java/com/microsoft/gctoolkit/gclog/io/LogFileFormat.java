// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.io;

/**
 * The physical format of a GC log source, as detected from the file system
 * entry itself (directory) or from the leading magic bytes of a regular file.
 */
public enum LogFileFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}
