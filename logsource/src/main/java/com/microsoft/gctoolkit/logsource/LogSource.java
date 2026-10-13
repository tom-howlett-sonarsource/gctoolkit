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

/** Common discovery and IO operations for file-backed GC log sources. */
public final class LogSource {
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int ZIP_MAGIC_FIRST = 0x50;
    private static final int ZIP_MAGIC_SECOND = 0x4b;

    private LogSource() {
    }

    /** Discovers the source format from its type and magic bytes. */
    public static SourceFormat discover(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return SourceFormat.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return SourceFormat.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return SourceFormat.ZIP;
            }
            return SourceFormat.PLAIN;
        }
    }

    /** Returns the number of uncompressed bytes in the readable log stream. */
    public static long byteSize(Path path) throws IOException {
        if (discover(path) == SourceFormat.PLAIN) {
            return Files.size(path);
        }
        try (InputStream input = open(path)) {
            byte[] buffer = new byte[8192];
            long size = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
            }
            return size;
        }
    }

    /** Opens the plain file or the first non-directory ZIP/GZIP content stream. */
    public static InputStream open(Path path) throws IOException {
        SourceFormat format = discover(path);
        if (format == SourceFormat.PLAIN) {
            return Files.newInputStream(path);
        }
        if (format == SourceFormat.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == SourceFormat.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                // Find the first file, preserving the callers' established ZIP behavior.
            }
            if (entry == null) {
                zip.close();
                throw new IOException("ZIP source contains no files: " + path);
            }
            return zip;
        }
        throw new IOException("Cannot open a directory as a log stream: " + path);
    }

    /** Opens the source as a lazily read stream of lines. */
    public static Stream<String> lines(Path path) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(open(path))));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked IO failures.
        }
    }
}
