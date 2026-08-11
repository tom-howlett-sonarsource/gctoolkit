// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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

/**
 * Common, dependency-free IO operations for file-backed GC log sources.
 */
public final class LogSource {
    private static final int GZIP_MAGIC = 0x1f8b;
    private static final int ZIP_MAGIC = 0x504b;

    private LogSource() {
    }

    public enum Format {
        PLAIN, ZIP, GZIP, DIRECTORY
    }

    /** Discover the source format from its type and magic bytes. */
    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            int magic = first << 8 | second;
            if (magic == GZIP_MAGIC) {
                return Format.GZIP;
            }
            if (magic == ZIP_MAGIC) {
                return Format.ZIP;
            }
            return Format.PLAIN;
        }
    }

    /** Return the stored byte size of a source. */
    public static long byteSize(Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /** Open plain text, the first file in a ZIP, or GZIP content as a byte stream. */
    public static InputStream open(Path path) throws IOException {
        Format format = discover(path);
        InputStream input = Files.newInputStream(path);
        try {
            if (format == Format.GZIP) {
                return new GZIPInputStream(input);
            }
            if (format == Format.ZIP) {
                ZipInputStream zip = new ZipInputStream(input);
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                    // Find the first file, preserving SingleGCLogFile's selection behavior.
                }
                if (entry == null) {
                    zip.close();
                    throw new IOException("ZIP source contains no files: " + path);
                }
                return zip;
            }
            if (format == Format.PLAIN) {
                return input;
            }
            throw new IOException("Unable to open directory as a log stream: " + path);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    /** Open a source as lazily decoded lines; closing the stream closes its input. */
    public static Stream<String> openLines(Path path) throws IOException {
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
