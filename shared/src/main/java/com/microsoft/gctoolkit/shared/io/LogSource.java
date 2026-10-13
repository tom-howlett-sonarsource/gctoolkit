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

/** Operations common to file-backed GC log sources. */
public final class LogSource {
    /** First byte in a GZIP header. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second byte in a GZIP header. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First byte in a ZIP header. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second byte in a ZIP header. */
    private static final int ZIP_MAGIC_2 = 0x4b;

    private LogSource() {
    }

    /** Supported kinds of GC log source. */
    public enum Format {
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** Uncompressed text file. */
        PLAINTEXT,
        /** Directory containing log segments. */
        DIRECTORY,
        /** Unrecognized source. */
        UNKNOWN
    }

    /**
     * Discovers a source from its file type and magic bytes.
     * @param path source path
     * @return discovered source format
     */
    public static Format discover(final Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC_1, GZIP_MAGIC_2)) {
            return Format.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC_1, ZIP_MAGIC_2)) {
            return Format.ZIP;
        }
        return Format.PLAINTEXT;
    }

    /**
     * Returns the on-disk byte size of a source.
     * @param path source path
     * @return on-disk size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(final Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Tests the first two bytes of a source.
     * @param path source path
     * @param first expected first byte
     * @param second expected second byte
     * @return {@code true} when both bytes match
     */
    public static boolean hasMagic(final Path path, final int first,
                                   final int second) {
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == first && input.read() == second;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Opens plain text, gzip, or the first non-directory ZIP entry.
     * @param path source path
     * @return input stream for uncompressed log bytes
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(final Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAINTEXT) {
            return Files.newInputStream(path);
        }
        if (format == Format.GZIP) {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        if (format == Format.ZIP) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    return zip;
                }
            }
            zip.close();
            throw new IOException(
                    "ZIP source contains no file entries: " + path);
        }
        throw new IOException("Unable to read " + path);
    }

    /**
     * Opens a source as a lazily read stream of lines.
     * @param path source path
     * @return lazy stream of log lines
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(final Path path) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(open(path))));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked exceptions.
        }
    }
}
