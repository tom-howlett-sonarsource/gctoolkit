// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The format of a GC log source. The format is derived from the leading, magic bytes of the
 * file the source is rooted in.
 */
public enum LogFileFormat {

    /** A ZIP compressed source. */
    ZIP,
    /** A GZIP compressed source. */
    GZIP,
    /** A regular, uncompressed source. */
    PLAINTEXT,
    /** A directory containing one or more sources. */
    DIRECTORY,
    /** The format of the source could not be determined. */
    UNKNOWN;

    private static final Logger LOG = Logger.getLogger(LogFileFormat.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Determine the format of the source found at the given path. A path that cannot be
     * recognised as a directory, or as a ZIP or GZIP source, is reported as
     * {@link #PLAINTEXT}.
     * @param path The path to the source.
     * @return The format of the source.
     */
    public static LogFileFormat of(Path path) {
        if (path == null)
            return UNKNOWN;
        else if (path.toFile().isDirectory())
            return DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return ZIP;
        else
            return PLAINTEXT;
    }

    /**
     * Compare the first two bytes of the file with the given magic bytes.
     * @param path The path to the source.
     * @param field1 The expected value of the first byte.
     * @param field2 The expected value of the second byte.
     * @return {@code true} if the leading bytes of the file match the magic bytes.
     */
    public static boolean magic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }
}
