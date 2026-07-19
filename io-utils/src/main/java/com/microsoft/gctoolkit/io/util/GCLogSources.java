// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Source-discovery and byte-sizing helpers for GC log files. The utilities
 * here inspect the first two bytes of a file to determine whether it is a
 * plain text, ZIP, or GZIP source and expose its size on disk.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    /** First magic byte of a GZIP stream. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second magic byte of a GZIP stream. */
    public static final int GZIP_MAGIC2 = 0x8b;

    /** First magic byte of a ZIP archive. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second magic byte of a ZIP archive. */
    public static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSources() {
    }

    /**
     * Detect the format of a GC log source by looking at its first two bytes.
     * Directories are reported as {@link GCLogSourceFormat#DIRECTORY}. Any
     * regular file whose magic bytes do not match ZIP or GZIP is reported as
     * {@link GCLogSourceFormat#PLAINTEXT}.
     *
     * @param path the source to inspect
     * @return the detected format, never {@code null}
     */
    public static GCLogSourceFormat detect(Path path) {
        if (path == null) {
            return GCLogSourceFormat.UNKNOWN;
        }
        if (path.toFile().isDirectory()) {
            return GCLogSourceFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return GCLogSourceFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return GCLogSourceFormat.ZIP;
        }
        return GCLogSourceFormat.PLAINTEXT;
    }

    /**
     * Compare the first two bytes of {@code path} against the supplied magic
     * bytes. Any IO problem is logged and reported as a mismatch.
     */
    public static boolean matchesMagic(Path path, int firstMagicByte, int secondMagicByte) {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == firstMagicByte && b2 == secondMagicByte;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return false;
        }
    }

    /**
     * Return the size of the file at {@code path} in bytes. Callers may use this
     * to size buffers or to report the on-disk footprint of a GC log source.
     *
     * @param path the file to size
     * @return the size of the file in bytes
     * @throws IOException if the size cannot be determined
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }
}
