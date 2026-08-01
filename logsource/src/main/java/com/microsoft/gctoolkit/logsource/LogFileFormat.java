// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The on disk format of a GC log source. The format is discovered from the magic bytes at the
 * head of the file, not from the file name.
 */
public enum LogFileFormat {

    /** A ZIP archive, which may hold more than one log source. */
    ZIP,
    /** A GZIP compressed log source. */
    GZIP,
    /** An uncompressed log source. */
    PLAINTEXT,
    /** A directory holding log sources. */
    DIRECTORY,
    /** The format has not been, or could not be, determined. */
    UNKNOWN;

    private static final Logger LOG = Logger.getLogger(LogFileFormat.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Discover the format of the source found at the given path. A path that cannot be read is
     * reported as {@link #PLAINTEXT}; opening it will fail later on with a more useful message.
     *
     * @param path The path to the log source.
     * @return The discovered format, never {@code null}.
     */
    public static LogFileFormat discover(Path path) {
        if (path.toFile().isDirectory())
            return DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return ZIP;
        else
            return PLAINTEXT;
    }

    /**
     * Report whether the source at the given path starts with the two given magic bytes.
     *
     * @param path The path to the log source.
     * @param first The expected value of the first byte.
     * @param second The expected value of the second byte.
     * @return {@code true} if both magic bytes match.
     */
    public static boolean magic(Path path, int first, int second) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            return magicByteReader.read() == first && magicByteReader.read() == second;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }
}
