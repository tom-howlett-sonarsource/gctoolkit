// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.sources.io;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Discovers the {@link LogFileFormat} of a GC log source by inspecting the first
 * two bytes of the file and testing for a directory.
 * <p>
 * The magic byte values matched here are the well known ZIP local file header
 * (<code>0x50 0x4B</code>, i.e. {@code "PK"}) and GZIP file header
 * (<code>0x1F 0x8B</code>).
 */
public final class LogFileFormatDetector {

    private static final Logger LOG = Logger.getLogger(LogFileFormatDetector.class.getName());

    /** First byte of the GZIP magic header. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second byte of the GZIP magic header. */
    public static final int GZIP_MAGIC2 = 0x8b;

    /** First byte of the ZIP magic header. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second byte of the ZIP magic header. */
    public static final int ZIP_MAGIC2 = 0x4b;

    private LogFileFormatDetector() {
        // static utility
    }

    /**
     * Detect the {@link LogFileFormat} for the given path.
     * <p>
     * When the path is a directory, {@link LogFileFormat#DIRECTORY} is returned.
     * Otherwise the two byte magic header of the file is compared to the known
     * ZIP and GZIP signatures, falling back to {@link LogFileFormat#PLAINTEXT}
     * when neither matches.
     *
     * @param path the path to probe; must not be {@code null}.
     * @return the discovered format.
     */
    public static LogFileFormat detect(Path path) {
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
     * Return {@code true} when the first two bytes of the file at {@code path}
     * match {@code field1} and {@code field2}.
     * <p>
     * IO errors are logged and treated as a non-match so callers can chain
     * probes without dealing with exceptions.
     *
     * @param path path to probe.
     * @param field1 expected value for the first byte.
     * @param field2 expected value for the second byte.
     * @return {@code true} when both bytes match; {@code false} otherwise.
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
