// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The formats a GC log source may be found in.
 */
public enum GCLogSourceFormat {

    /**
     * A ZIP compressed file which may contain more than one log.
     */
    ZIP,
    /**
     * A GZip compressed file.
     */
    GZIP,
    /**
     * A regular, uncompressed file.
     */
    PLAINTEXT,
    /**
     * A directory, which may contain the segments of a rotating log.
     */
    DIRECTORY;

    private static final Logger LOG = Logger.getLogger(GCLogSourceFormat.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Discover the format of the source found at the given path. A path that cannot be
     * read is reported as {@link #PLAINTEXT}, leaving it to the caller to fail when the
     * source is opened.
     * @param path The path to the source.
     * @return The discovered format, never {@code null}.
     */
    public static GCLogSourceFormat discover(Path path) {
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
     * {@code true} if the source starts with the two given magic bytes.
     * @param path The path to the source.
     * @param field1 The expected first byte.
     * @param field2 The expected second byte.
     * @return {@code true} if both magic bytes match.
     */
    static boolean magic(Path path, int field1, int field2) {
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
