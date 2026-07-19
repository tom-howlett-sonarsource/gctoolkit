// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Detects the {@link LogFileFormat} of a GC log source by reading its leading magic bytes.
 */
public final class LogFileFormatDetector {

    private static final Logger LOG = Logger.getLogger(LogFileFormatDetector.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private LogFileFormatDetector() {
    }

    /**
     * Classify the given path as a directory, ZIP, GZIP, or plain text source.
     * A path that cannot be read is reported as {@link LogFileFormat#PLAINTEXT}
     * to preserve the historical behaviour of the callers.
     * @param path Path to classify.
     * @return the detected format.
     */
    public static LogFileFormat detect(Path path) {
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} when the first two bytes of the file match the supplied
     * magic-byte pair. Any I/O error is logged and treated as no-match.
     * @param path Path to check.
     * @param field1 Expected first byte.
     * @param field2 Expected second byte.
     * @return whether the leading bytes match.
     */
    public static boolean hasMagic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return false;
        }
    }
}
