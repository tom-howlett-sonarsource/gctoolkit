// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

/**
 * Log file formats recognised by the shared byte-sizing detector.
 */
public enum LogStreamFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}
