// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** File-system and compressed-stream operations shared by GC log consumers. */
public final class GCLogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private GCLogSource() {
    }

    public enum Format { ZIP, GZIP, PLAIN_TEXT, DIRECTORY }

    /** Determine source format from the path and file signature, not its suffix. */
    public static Format format(Path path) throws IOException {
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

    /** Return the number of bytes occupied by the source on disk. */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /** Discover direct file children, or return the file itself for a single source. */
    public static List<Path> discover(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Collections.singletonList(path);
        }
        try (var children = Files.list(path)) {
            return children.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    /** Discover the names of all file entries in a ZIP source. */
    public static List<String> discoverZipEntries(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.stream().filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName).collect(Collectors.toList());
        }
    }

    /** Open plain, gzip, or the first non-directory ZIP entry as one byte stream. */
    public static InputStream open(Path path) throws IOException {
        Format format = format(path);
        if (format == Format.PLAIN_TEXT) {
            return new BufferedInputStream(Files.newInputStream(path));
        }
        if (format == Format.GZIP) {
            return new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)));
        }
        if (format == Format.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                // Find the first log entry.
            }
            if (entry == null) {
                zip.close();
                throw new IOException("ZIP source contains no file entries: " + path);
            }
            return new BufferedInputStream(zip);
        }
        throw new IOException("Cannot open a directory as a log stream: " + path);
    }

    /** Open a named entry and ensure closing the stream also closes its ZIP file. */
    public static InputStream open(Path path, String entryName) throws IOException {
        ZipFile zip = new ZipFile(path.toFile());
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            zip.close();
            throw new IOException("ZIP entry not found: " + entryName);
        }
        InputStream input = zip.getInputStream(entry);
        return new BufferedInputStream(input) {
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
