// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File format of a GC log source.
 */
public enum GCLogSourceFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN;

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    /**
     * Detect the source format from the path and leading magic bytes.
     *
     * @param path path to inspect
     * @return detected format
     * @throws IOException when the path cannot be inspected
     */
    public static GCLogSourceFormat from(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return DIRECTORY;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            int magicByte1 = inputStream.read();
            int magicByte2 = inputStream.read();
            if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2) {
                return GZIP;
            }
            if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2) {
                return ZIP;
            }
            return PLAINTEXT;
        }
    }
}
