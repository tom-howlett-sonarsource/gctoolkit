// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The formats of GC log source that GCToolKit is able to read.
 */
public enum LogSourceFormat {

    /**
     * A directory that contains log files.
     */
    DIRECTORY,

    /**
     * A GZip compressed log file.
     */
    GZIP,

    /**
     * A regular, uncompressed log file.
     */
    PLAINTEXT,

    /**
     * A Zip compressed log file.
     */
    ZIP;

    private static final Logger LOGGER = Logger.getLogger(LogSourceFormat.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Discover the format of the log source rooted at the given path. A source that is neither a
     * directory nor recognizable, from its leading magic bytes, as a compressed file is taken to be
     * plain text.
     * @param path The path to the log source.
     * @return The format of the log source.
     */
    public static LogSourceFormat discover(Path path) {
        return Files.isDirectory(path) ? DIRECTORY : magic(path);
    }

    private static LogSourceFormat magic(Path path) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2)
                return GZIP;
            if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2)
                return ZIP;
        } catch (IOException ioe) {
            LOGGER.warning(ioe.getMessage());
        }
        return PLAINTEXT;
    }
}
