// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Operations common to file-backed GC log sources. */
public final class LogSource {
    /** First byte of the gzip signature. */
    private static final int GZIP_MAGIC_1 = 0x1f;
    /** Second byte of the gzip signature. */
    private static final int GZIP_MAGIC_2 = 0x8b;
    /** First byte of the ZIP signature. */
    private static final int ZIP_MAGIC_1 = 0x50;
    /** Second byte of the ZIP signature. */
    private static final int ZIP_MAGIC_2 = 0x4b;
    /** Buffer size used while counting expanded bytes. */
    private static final int BUFFER_SIZE = 8192;

    private LogSource() { }

    /** Supported kinds of GC log source. */
    public enum Format {
        /** An uncompressed file. */
        PLAIN,
        /** A ZIP archive. */
        ZIP,
        /** A gzip stream. */
        GZIP,
        /** A filesystem directory. */
        DIRECTORY
    }

    /**
     * Discovers the source kind from the filesystem and its magic bytes.
     * @param path source to inspect
     * @return discovered source format
     * @throws IOException if the source cannot be inspected
     */
    public static Format discover(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC_1, GZIP_MAGIC_2)) {
            return Format.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC_1, ZIP_MAGIC_2)) {
            return Format.ZIP;
        }
        return Format.PLAIN;
    }

    /**
     * Tests the first two bytes of a source.
     * @param path source to inspect
     * @param first expected first byte
     * @param second expected second byte
     * @return whether both bytes match
     * @throws IOException if the source cannot be read
     */
    public static boolean hasMagic(final Path path, final int first,
                                   final int second) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == first && input.read() == second;
        }
    }

    /**
     * Returns the number of readable, uncompressed bytes in a source.
     * @param path source to size
     * @return expanded byte count
     * @throws IOException if the source cannot be read
     */
    public static long size(final Path path) throws IOException {
        if (discover(path) == Format.PLAIN) {
            return Files.size(path);
        }
        if (Files.isDirectory(path)) {
            throw new IOException("Unable to size directory " + path);
        }
        long count = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = open(path)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                count += read;
            }
        }
        return count;
    }

    /**
     * Opens a plain file, gzip payload, or first non-directory ZIP entry.
     * @param path source to open
     * @return expanded source stream
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(final Path path) throws IOException {
        switch (discover(path)) {
            case PLAIN:
                return new BufferedInputStream(Files.newInputStream(path));
            case GZIP: return new GZIPInputStream(Files.newInputStream(path));
            case ZIP: return openZip(path);
            default: throw new IOException("Unable to open directory " + path);
        }
    }

    private static InputStream openZip(final Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            input.closeEntry();
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP contains no file entries: " + path);
        }
        return new FilterInputStream(new BufferedInputStream(input)) { };
    }
}
