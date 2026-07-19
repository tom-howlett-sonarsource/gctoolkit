// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

/**
 * Enumeration of the log file layouts recognized by {@link LogFileFormatDetector}.
 */
public enum LogFileFormat {
    /** ZIP archive. */
    ZIP,
    /** GZIP compressed file. */
    GZIP,
    /** Regular plain-text file. */
    PLAINTEXT,
    /** Directory of files. */
    DIRECTORY,
    /** File exists but its format could not be determined. */
    UNKNOWN
}
