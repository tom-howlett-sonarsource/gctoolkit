// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The storage format of a GC log source. The format determines how the source is discovered and
 * how a stream of log lines is opened over it.
 */
public enum LogFileFormat {

    /** A ZIP archive holding one or more log files. */
    ZIP,
    /** A GZip compressed log file. */
    GZIP,
    /** An uncompressed log file. */
    PLAINTEXT,
    /** A directory holding one or more log files. */
    DIRECTORY;

    private static final Logger LOGGER = Logger.getLogger(LogFileFormat.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Determine the format of the log source at the given path from the leading magic bytes of the
     * file. A source that cannot be read is reported as {@link #PLAINTEXT}.
     * @param path The path to the log source.
     * @return The format of the log source.
     */
    public static LogFileFormat detect(Path path) {
        if (Files.isDirectory(path))
            return DIRECTORY;
        else if (startsWith(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return GZIP;
        else if (startsWith(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return ZIP;
        else
            return PLAINTEXT;
    }

    private static boolean startsWith(Path path, int firstByte, int secondByte) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            return magicByteReader.read() == firstByte && magicByteReader.read() == secondByte;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return false;
    }
}
