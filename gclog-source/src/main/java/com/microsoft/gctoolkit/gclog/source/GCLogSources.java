// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared production helpers for GC log sources: format discovery from magic bytes,
 * byte sizing, and opening plain, ZIP, and GZIP log streams as line streams.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8B;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSources() {
    }

    /**
     * Discover the {@link SourceFormat} of the file at {@code path} by inspecting
     * the first two bytes (magic number). Returns {@link SourceFormat#DIRECTORY} for
     * directories and {@link SourceFormat#PLAINTEXT} for any regular file whose
     * magic bytes do not match ZIP or GZIP.
     *
     * @param path the source path
     * @return the discovered format
     */
    public static SourceFormat detectFormat(Path path) {
        if (path.toFile().isDirectory()) {
            return SourceFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return SourceFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return SourceFormat.ZIP;
        }
        return SourceFormat.PLAINTEXT;
    }

    /**
     * Return the size of the file at {@code path} in bytes.
     *
     * @param path the source path
     * @return the file size in bytes
     * @throws IOException if the size cannot be read
     */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open the file at {@code path} as a stream of lines, choosing the reader by
     * {@code format}. Supported: {@link SourceFormat#PLAINTEXT}, {@link SourceFormat#ZIP},
     * {@link SourceFormat#GZIP}.
     *
     * @param path   the source path
     * @param format the format of the source (from {@link #detectFormat(Path)})
     * @return a stream of lines from the source
     * @throws IOException if the source cannot be opened, or if {@code format} is not streamable
     */
    public static Stream<String> openLines(Path path, SourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlainText(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGZip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open the plain-text file at {@code path} as a stream of lines.
     */
    public static Stream<String> openPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry of the ZIP file at {@code path} as a stream of lines.
     * Closing the returned stream also closes the underlying ZIP input.
     */
    @SuppressWarnings("resource")
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Open the GZIP file at {@code path} as a stream of lines.
     * Closing the returned stream also closes the underlying GZIP input.
     */
    @SuppressWarnings("resource")
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, ioe.getMessage(), ioe);
        }
    }

    private static boolean matchesMagic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, ioe.getMessage(), ioe);
        }
        return false;
    }
}
