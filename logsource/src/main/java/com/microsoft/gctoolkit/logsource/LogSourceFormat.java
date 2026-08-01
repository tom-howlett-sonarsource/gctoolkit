// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The format of a GC log source. The format is discovered from the source itself, that is, from
 * the magic bytes at the head of the file rather than from the name of the file.
 */
public enum LogSourceFormat {

    /**
     * A Zip compressed log.
     */
    ZIP,
    /**
     * A GZip compressed log.
     */
    GZIP,
    /**
     * A regular, uncompressed log.
     */
    PLAINTEXT,
    /**
     * A directory, which may contain log file segments.
     */
    DIRECTORY,
    /**
     * The format could not be discovered.
     */
    UNKNOWN;

    private static final Logger LOG = Logger.getLogger(LogSourceFormat.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Discover the format of the source found at the given path. A source that can be read but
     * carries neither the Zip nor the GZip magic bytes is considered to be plain text.
     *
     * @param path The path to the log source.
     * @return The format of the source, {@link #UNKNOWN} if the path is {@code null}.
     */
    public static LogSourceFormat of(Path path) {
        if (path == null)
            return UNKNOWN;
        if (Files.isDirectory(path))
            return DIRECTORY;

        int[] magic = magic(path);
        if (matches(magic, GZIP_MAGIC1, GZIP_MAGIC2))
            return GZIP;
        if (matches(magic, ZIP_MAGIC1, ZIP_MAGIC2))
            return ZIP;
        return PLAINTEXT;
    }

    private static int[] magic(Path path) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            return new int[]{magicByteReader.read(), magicByteReader.read()};
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return new int[]{-1, -1};
    }

    private static boolean matches(int[] magic, int first, int second) {
        return magic[0] == first && magic[1] == second;
    }
}
