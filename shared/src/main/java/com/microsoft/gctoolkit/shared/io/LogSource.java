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
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** File-system operations common to GC log sources. */
public final class LogSource {
    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSource() {
    }

    /** The supported kinds of log source. */
    public enum Format {
        ZIP, GZIP, PLAINTEXT, DIRECTORY, UNKNOWN
    }

    /** Discovers the source kind from the file type and compression magic bytes. */
    public static Format discover(Path path) {
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
            return Format.PLAINTEXT;
        } catch (IOException ignored) {
            return Format.UNKNOWN;
        }
    }

    /** Returns the source's size in bytes. */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /** Opens a line stream, transparently reading plain, ZIP, or GZIP sources. */
    public static Stream<String> lines(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAINTEXT) {
            return Files.lines(path);
        }
        if (format == Format.GZIP) {
            return bufferedLines(new GZIPInputStream(Files.newInputStream(path)));
        }
        if (format == Format.ZIP) {
            ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return bufferedLines(input);
        }
        throw new IOException("Unable to read " + path);
    }

    private static Stream<String> bufferedLines(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
    }
}
