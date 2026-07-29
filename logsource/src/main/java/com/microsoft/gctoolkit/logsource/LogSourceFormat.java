// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The kinds of GC log source that can be read. The format of a source is discovered from the
 * leading magic bytes of the file it is held in.
 */
public enum LogSourceFormat {

    /**
     * A Zip compressed source.
     */
    ZIP,
    /**
     * A GZip compressed source.
     */
    GZIP,
    /**
     * A regular, uncompressed source.
     */
    PLAINTEXT,
    /**
     * A directory holding one or more sources.
     */
    DIRECTORY,
    /**
     * A source that cannot be identified.
     */
    UNKNOWN;

    private static final Logger LOG = Logger.getLogger(LogSourceFormat.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private static final int MAGIC_BYTES = 2;

    /**
     * Discover the format of the log source found at the given path. A source that is too small,
     * or that cannot be read, to carry the magic bytes of a compressed file is reported as
     * {@link #PLAINTEXT}.
     * @param path The path to the log source.
     * @return The format of the log source, {@link #UNKNOWN} if the path is {@code null}.
     */
    public static LogSourceFormat of(Path path) {
        if (path == null)
            return UNKNOWN;
        if (Files.isDirectory(path))
            return DIRECTORY;
        if (LogSourceFiles.sizeInBytes(path) < MAGIC_BYTES)
            return PLAINTEXT;
        if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return GZIP;
        if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return ZIP;
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
