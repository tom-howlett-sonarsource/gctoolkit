// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Detected format of a GC log source file, determined by reading the first two
 * magic bytes of the file.
 */
public enum FileFormat {

    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN;

    private static final Logger LOG = Logger.getLogger(FileFormat.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Detect the format of the file at the given path by reading its magic
     * bytes. Directories are detected via {@link java.io.File#isDirectory()}.
     *
     * @param path path to the file
     * @return the detected {@code FileFormat}
     */
    public static FileFormat detect(Path path) {
        if (path.toFile().isDirectory()) {
            return DIRECTORY;
        }
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            int b1 = fis.read();
            int b2 = fis.read();
            if (b1 == GZIP_MAGIC1 && b2 == GZIP_MAGIC2) return GZIP;
            if (b1 == ZIP_MAGIC1 && b2 == ZIP_MAGIC2) return ZIP;
        } catch (IOException e) {
            LOG.warning(e.getMessage());
        }
        return PLAINTEXT;
    }
}
