// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The on-disk format of a GC log source. The format is discovered by inspecting
 * the leading ("magic") bytes of the file, or by recognising a directory.
 */
public enum LogFileFormat {
    /** A ZIP compressed archive. */
    ZIP,
    /** A GZIP compressed file. */
    GZIP,
    /** An uncompressed, plain text file. */
    PLAINTEXT,
    /** A directory containing log file segments. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN;

    private static final Logger LOG = Logger.getLogger(LogFileFormat.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Discover the format of the source at the given path. Directories are
     * recognised directly; regular files are classified by reading their leading
     * magic bytes. A source that cannot be read or does not match a known
     * compression signature is reported as {@link #PLAINTEXT}.
     * @param path The path to the source.
     * @return The discovered {@link LogFileFormat}.
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
     * Read the first two bytes of the file and compare them to the supplied
     * signature.
     * @param path The path to the source.
     * @param field1 The expected value of the first byte.
     * @param field2 The expected value of the second byte.
     * @return {@code true} if the leading bytes match the signature.
     */
    static boolean magic(Path path, int field1, int field2) {
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
