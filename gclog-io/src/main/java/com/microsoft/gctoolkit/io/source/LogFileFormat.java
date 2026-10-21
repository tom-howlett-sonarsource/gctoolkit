// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Shared utilities for discovering the format of a GC log source and for reading
 * the leading "magic" bytes used to distinguish plain text, ZIP and GZIP sources.
 * <p>
 * This behaviour was previously duplicated across the {@code api} and {@code parser}
 * modules. It is collected here so that both modules share a single implementation.
 */
public final class LogFileFormat {

    private static final Logger LOG = Logger.getLogger(LogFileFormat.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    /**
     * The kinds of GC log source that can be discovered from a {@link Path}.
     */
    public enum Kind {
        /** A ZIP compressed source. */
        ZIP,
        /** A GZIP compressed source. */
        GZIP,
        /** A regular, plain text source. */
        PLAINTEXT,
        /** A directory of sources. */
        DIRECTORY,
        /** The format could not be determined. */
        UNKNOWN
    }

    private LogFileFormat() {
    }

    /**
     * Discover the {@link Kind} of source referenced by the given path by inspecting
     * whether it is a directory and, if not, reading its leading magic bytes.
     * @param path the path to the source.
     * @return the discovered {@link Kind}; {@link Kind#PLAINTEXT} when no compression
     * magic is recognised.
     */
    public static Kind discover(Path path) {
        if (path.toFile().isDirectory())
            return Kind.DIRECTORY;
        else if (magic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return Kind.GZIP;
        else if (magic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return Kind.ZIP;
        else
            return Kind.PLAINTEXT;
    }

    /**
     * Read the first two bytes of the file and test them against the supplied magic values.
     * @param path the path to the file.
     * @param field1 the expected first byte.
     * @param field2 the expected second byte.
     * @return {@code true} if the first two bytes match the supplied magic values.
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
