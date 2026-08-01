// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The format in which a GC log source is presented.
 */
public enum LogFileFormat {

    /**
     * A ZIP compressed source which may hold more than one log file.
     */
    ZIP,

    /**
     * A GZIP compressed source.
     */
    GZIP,

    /**
     * A regular, uncompressed log file.
     */
    PLAINTEXT,

    /**
     * A directory, typically holding the segments of a rotating log.
     */
    DIRECTORY,

    /**
     * The format has not been, or could not be, determined.
     */
    UNKNOWN;

    private static final Logger LOG = Logger.getLogger(LogFileFormat.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Determine the format of the source found at the given path. Compressed formats are
     * recognised by their leading, magic bytes. A source that is neither a directory nor
     * a recognised compressed file, including one that cannot be read, is reported as
     * {@link #PLAINTEXT}.
     *
     * @param path The path to the source.
     * @return The format of the source.
     */
    public static LogFileFormat detect(Path path) {
        if (Files.isDirectory(path))
            return DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return ZIP;
        else
            return PLAINTEXT;
    }

    private static boolean magic(Path path, int field1, int field2) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }
}
