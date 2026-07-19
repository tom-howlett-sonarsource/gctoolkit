// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Detects the on-disk format of a GC log source by examining the first two
 * bytes of the file (or by identifying a directory). Consolidates the magic
 * byte checks previously duplicated across the API and parser modules.
 */
public final class LogFileFormatDetector {

    private static final Logger LOG = Logger.getLogger(LogFileFormatDetector.class.getName());

    public static final int GZIP_MAGIC1 = 0x1F;
    public static final int GZIP_MAGIC2 = 0x8B;

    public static final int ZIP_MAGIC1 = 0x50;
    public static final int ZIP_MAGIC2 = 0x4B;

    private LogFileFormatDetector() {
    }

    /**
     * Return the {@link LogFileFormat} for the given path.
     * @param path The path to inspect.
     * @return the detected format; {@link LogFileFormat#PLAINTEXT} when no
     *         magic bytes match and the path is a regular file.
     */
    public static LogFileFormat detect(Path path) {
        if (path.toFile().isDirectory()) {
            return LogFileFormat.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogFileFormat.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogFileFormat.ZIP;
        }
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Read the first two bytes of {@code path} and compare against the given
     * magic byte pair.
     * @param path   The path to read.
     * @param field1 The first expected byte.
     * @param field2 The second expected byte.
     * @return {@code true} when both bytes match; {@code false} otherwise
     *         (including on I/O failure).
     */
    public static boolean hasMagic(Path path, int field1, int field2) {
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
