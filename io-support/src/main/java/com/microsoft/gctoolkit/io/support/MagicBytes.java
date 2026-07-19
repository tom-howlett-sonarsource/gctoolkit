// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Byte-sizing helpers used to identify the on-disk format of a GC log file
 * from its first two "magic" bytes.
 */
public final class MagicBytes {

    private static final Logger LOG = Logger.getLogger(MagicBytes.class.getName());

    public static final int GZIP_MAGIC1 = 0x1F;
    public static final int GZIP_MAGIC2 = 0x8b;

    public static final int ZIP_MAGIC1 = 0x50;
    public static final int ZIP_MAGIC2 = 0x4b;

    private MagicBytes() {
    }

    /**
     * Return {@code true} when the first two bytes of the file at {@code path}
     * match {@code field1} and {@code field2}. Any IO error is logged and
     * results in {@code false}.
     */
    public static boolean matches(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Classify the file at {@code path} by its type and, for regular files,
     * by the leading two bytes.
     */
    public static LogStreamFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return LogStreamFormat.DIRECTORY;
        }
        if (matches(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogStreamFormat.GZIP;
        }
        if (matches(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogStreamFormat.ZIP;
        }
        return LogStreamFormat.PLAINTEXT;
    }
}
