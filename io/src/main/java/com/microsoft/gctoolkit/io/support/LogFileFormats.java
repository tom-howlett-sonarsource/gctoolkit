// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classifies a filesystem path as one of the {@link LogFileFormat} values by
 * inspecting the first two bytes of the file (for compressed formats) or by
 * checking whether the path is a directory.
 */
public final class LogFileFormats {

    private static final Logger LOG = Logger.getLogger(LogFileFormats.class.getName());

    /** First byte of the GZIP magic (RFC 1952). */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second byte of the GZIP magic (RFC 1952). */
    public static final int GZIP_MAGIC2 = 0x8B;

    /** First byte of the ZIP local file header signature. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second byte of the ZIP local file header signature. */
    public static final int ZIP_MAGIC2 = 0x4B;

    private LogFileFormats() {
    }

    /**
     * Detect the log format of the given path.
     *
     * @param path the file or directory to inspect; must not be {@code null}
     * @return the detected {@link LogFileFormat}
     */
    public static LogFileFormat detect(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
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
     * Returns {@code true} when the first two bytes of {@code path} match
     * {@code field1} and {@code field2} respectively.
     *
     * @param path   the file to inspect
     * @param field1 expected first byte
     * @param field2 expected second byte
     * @return whether the file's leading bytes match
     */
    public static boolean hasMagic(Path path, int field1, int field2) {
        Objects.requireNonNull(path, "path");
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, ioe.getMessage(), ioe);
        }
        return false;
    }
}
