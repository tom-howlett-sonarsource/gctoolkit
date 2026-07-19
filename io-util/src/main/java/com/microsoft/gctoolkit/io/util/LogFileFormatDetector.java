// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Detects the {@link LogFileFormat} of a GC log source by inspecting the first two
 * bytes (the ZIP or GZIP magic number) or by testing for a directory.
 */
public final class LogFileFormatDetector {

    private static final Logger LOG = Logger.getLogger(LogFileFormatDetector.class.getName());

    /** First byte of the GZIP magic number. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second byte of the GZIP magic number. */
    public static final int GZIP_MAGIC2 = 0x8b;
    /** First byte of the ZIP magic number ({@code 'P'}). */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second byte of the ZIP magic number ({@code 'K'}). */
    public static final int ZIP_MAGIC2 = 0x4b;

    private LogFileFormatDetector() {
        // static helpers only
    }

    /**
     * Detects the format of the given path.
     *
     * @param path path to a file or directory.
     * @return the detected {@link LogFileFormat}, or {@link LogFileFormat#UNKNOWN} if the
     *         file cannot be inspected.
     */
    public static LogFileFormat detect(Path path) {
        if (path == null) {
            return LogFileFormat.UNKNOWN;
        }
        if (path.toFile().isDirectory()) {
            return LogFileFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogFileFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogFileFormat.ZIP;
        }
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Reads the first two bytes of the file and tests them against the supplied
     * magic-number pair.
     *
     * @param path   path to the file.
     * @param field1 expected first byte.
     * @param field2 expected second byte.
     * @return {@code true} if the file starts with the given two bytes.
     */
    public static boolean matchesMagic(Path path, int field1, int field2) {
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
