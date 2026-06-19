// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Filesystem metadata for a GC log source.
 */
public class LogSourceMetadata {

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private final Path path;
    private final LogSourceFileType fileType;

    public LogSourceMetadata(Path path) throws IOException {
        this.path = path;
        this.fileType = discoverFileType(path);
    }

    public Path getPath() {
        return path;
    }

    public boolean isZip() {
        return fileType == LogSourceFileType.ZIP;
    }

    public boolean isGZip() {
        return fileType == LogSourceFileType.GZIP;
    }

    public boolean isPlainText() {
        return fileType == LogSourceFileType.PLAINTEXT;
    }

    public boolean isDirectory() {
        return fileType == LogSourceFileType.DIRECTORY;
    }

    public LogSourceFileType getFileType() {
        return fileType;
    }

    private static LogSourceFileType discoverFileType(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return LogSourceFileType.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return LogSourceFileType.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return LogSourceFileType.ZIP;
        }
        return LogSourceFileType.PLAINTEXT;
    }

    private static boolean hasMagic(Path path, int firstExpectedByte, int secondExpectedByte) throws IOException {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            return magicByteReader.read() == firstExpectedByte
                    && magicByteReader.read() == secondExpectedByte;
        }
    }
}
