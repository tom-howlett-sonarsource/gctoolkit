// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Metadata discovered from a GC log source path.
 */
public class LogSourceMetadata {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private final Path path;
    private final LogSourceFormat format;

    public LogSourceMetadata(Path path) throws IOException {
        this.path = path;
        this.format = detectFormat(path);
    }

    public Path getPath() {
        return path;
    }

    public LogSourceFormat getFormat() {
        return format;
    }

    public int getNumberOfFiles() throws IOException {
        if (format == LogSourceFormat.ZIP) {
            return LogSourceStreams.zipEntryNames(path).size();
        }
        if (format == LogSourceFormat.DIRECTORY) {
            try (var paths = Files.list(path)) {
                return Math.toIntExact(paths.count());
            }
        }
        return format == LogSourceFormat.UNKNOWN ? 0 : 1;
    }

    private static LogSourceFormat detectFormat(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return LogSourceFormat.DIRECTORY;
        }
        if (!Files.exists(path)) {
            return LogSourceFormat.UNKNOWN;
        }
        if (hasMagic(path, GZIP_MAGIC_BYTE_1, GZIP_MAGIC_BYTE_2)) {
            return LogSourceFormat.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC_BYTE_1, ZIP_MAGIC_BYTE_2)) {
            return LogSourceFormat.ZIP;
        }
        return Files.exists(path) ? LogSourceFormat.PLAINTEXT : LogSourceFormat.UNKNOWN;
    }

    private static boolean hasMagic(Path path, int firstExpected, int secondExpected) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return inputStream.read() == firstExpected && inputStream.read() == secondExpected;
        }
    }
}
