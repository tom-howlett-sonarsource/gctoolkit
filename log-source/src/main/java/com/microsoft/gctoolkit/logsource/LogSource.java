// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Shared filesystem and compressed-stream operations for GC log sources. */
public final class LogSource {
    private static final int GZIP_MAGIC1 = 0x1f;
    private static final int GZIP_MAGIC2 = 0x8b;
    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSource() {
    }

    public enum Format {
        ZIP, GZIP, PLAINTEXT, DIRECTORY
    }

    /** Discovers source format from filesystem type and magic bytes. */
    public static Format discover(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC1 && second == GZIP_MAGIC2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC1 && second == ZIP_MAGIC2) {
                return Format.ZIP;
            }
            return Format.PLAINTEXT;
        }
    }

    /** Returns the number of bytes occupied by the source file. */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /** Opens a plain, GZIP, or the first non-directory ZIP entry as lines. */
    public static Stream<String> open(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to read directory " + path);
        }

        InputStream input = Files.newInputStream(path);
        try {
            if (format == Format.GZIP) {
                input = new GZIPInputStream(input);
            } else if (format == Format.ZIP) {
                ZipInputStream zip = new ZipInputStream(input);
                ZipEntry entry;
                do {
                    entry = zip.getNextEntry();
                } while (entry != null && entry.isDirectory());
                if (entry == null) {
                    zip.close();
                    throw new IOException("ZIP source contains no files: " + path);
                }
                input = zip;
            }
            return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }
}
