package com.microsoft.gctoolkit.shared.io;

/**
 * Supported GC log source types.
 */
public enum LogSourceType {
    /** ZIP archive. */
    ZIP,
    /** GZIP-compressed file. */
    GZIP,
    /** Plain text file. */
    PLAIN_TEXT,
    /** File-system directory. */
    DIRECTORY
}
