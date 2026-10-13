// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * IO operations shared by consumers of GC log sources.
 */
public final class GCLogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    /** Supported source formats. */
    public enum Format {
        PLAIN_TEXT, ZIP, GZIP, DIRECTORY
    }

    /** Discovers a source format from the filesystem and its magic bytes. */
    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /** Returns the source's size on disk in bytes. */
    public static long byteSize(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /** Opens a plain, gzip, or the first file entry of a zip source. */
    public static InputStream open(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAIN_TEXT) {
            return new BufferedInputStream(Files.newInputStream(path));
        }
        if (format == Format.GZIP) {
            return new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)));
        }
        if (format == Format.ZIP) {
            ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
                // Find the first file, matching the historic single-source behavior.
            }
            if (entry == null) {
                input.close();
                throw new IOException("ZIP source contains no file entries: " + path);
            }
            return new BufferedInputStream(input);
        }
        throw new IOException("Cannot open a directory as a log stream: " + path);
    }

    /** Opens a named file entry from a zip source. */
    public static InputStream openZipEntry(Path path, String entryName) throws IOException {
        ZipFile zip = new ZipFile(path.toFile());
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zip.close();
            throw new IOException("ZIP entry not found: " + entryName);
        }
        return new BufferedInputStream(zip.getInputStream(entry)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zip.close();
                }
            }
        };
    }
}
