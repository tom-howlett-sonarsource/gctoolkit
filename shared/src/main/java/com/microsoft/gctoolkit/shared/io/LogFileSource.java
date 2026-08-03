// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** IO operations shared by GC log consumers. */
public final class LogFileSource {
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private LogFileSource() {
    }

    /** Supported kinds of GC log source. */
    public enum Format {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY
    }

    /** Discovers source format from its type and magic bytes. */
    public static Format discover(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
        } catch (IOException ignored) {
            // Preserve the existing behavior: unreadable non-directories are treated as plain text.
        }
        return Format.PLAINTEXT;
    }

    /** Returns the source's size in bytes. */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /** Opens source lines, using the first non-directory ZIP entry when applicable. */
    public static Stream<String> open(Path path) throws IOException {
        Format format = discover(path);
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return lines(openZip(path));
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            case DIRECTORY:
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private static InputStream openZip(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = input.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + path);
        }
        return input;
    }

    private static Stream<String> lines(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
    }
}
