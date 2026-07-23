// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Detects the format of a log source file by inspecting its magic bytes.
 */
public final class FormatDetector {

    private static final Logger LOG = Logger.getLogger(FormatDetector.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8B;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4B;

    private FormatDetector() {}

    /**
     * Detect the format of the file at the given path.
     * @param path path to the file
     * @return the detected {@link FileFormat}
     */
    public static FileFormat detect(Path path) {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        } else if (magicMatches(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        } else if (magicMatches(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        } else {
            return FileFormat.PLAINTEXT;
        }
    }

    private static boolean magicMatches(Path path, int expected1, int expected2) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int byte1 = in.read();
            int byte2 = in.read();
            return byte1 == expected1 && byte2 == expected2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }
}
