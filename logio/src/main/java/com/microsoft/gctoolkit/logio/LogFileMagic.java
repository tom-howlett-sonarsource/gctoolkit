// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Discovers the {@link LogFileFormat} of a path by reading a small
 * fixed-size header (two "magic" bytes) and comparing against the
 * signatures for ZIP and GZIP. Directories are reported as
 * {@link LogFileFormat#DIRECTORY} without any IO. Any other regular
 * file is reported as {@link LogFileFormat#PLAINTEXT}.
 */
public final class LogFileMagic {

    private static final Logger LOG = Logger.getLogger(LogFileMagic.class.getName());

    /** First magic byte of a GZIP stream. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second magic byte of a GZIP stream. */
    public static final int GZIP_MAGIC2 = 0x8b;

    /** First magic byte of a ZIP archive. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second magic byte of a ZIP archive. */
    public static final int ZIP_MAGIC2 = 0x4b;

    private LogFileMagic() {
        // utility
    }

    /**
     * Detect the format of the file (or directory) at the given path.
     * IO errors while reading the magic bytes are logged and treated
     * as "not that format" — a regular file whose header could not be
     * read is reported as {@link LogFileFormat#PLAINTEXT}.
     *
     * @param path path to inspect
     * @return the discovered format
     */
    public static LogFileFormat detect(Path path) {
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        if (matches(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        if (matches(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Read the first two bytes of the file at {@code path} and compare
     * them against the given magic signature.
     *
     * @param path   file to read
     * @param field1 expected first byte
     * @param field2 expected second byte
     * @return {@code true} iff both bytes match the signature
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
}
