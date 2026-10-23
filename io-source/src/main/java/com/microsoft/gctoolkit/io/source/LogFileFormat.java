// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The on-disk format of a GC log source. Instances are discovered from a {@link Path} by
 * inspecting whether the path is a directory and, if not, by sizing up its leading
 * (magic) bytes.
 */
public enum LogFileFormat {
    /** A ZIP compressed file. */
    ZIP,
    /** A GZip compressed file. */
    GZIP,
    /** A regular, uncompressed text file. */
    PLAINTEXT,
    /** A directory of log segments. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN;

    private static final Logger LOG = Logger.getLogger(LogFileFormat.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Discover the format of the source at the given path. Directories are reported as
     * {@link #DIRECTORY}; otherwise the leading two bytes are read to distinguish GZip and
     * ZIP archives from plain text.
     * @param path The path to the GC log source.
     * @return The discovered {@link LogFileFormat}.
     */
    public static LogFileFormat detect(Path path) {
        if (path.toFile().isDirectory())
            return DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return ZIP;
        else
            return PLAINTEXT;
    }

    private static boolean magic(Path path, int field1, int field2) {
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
