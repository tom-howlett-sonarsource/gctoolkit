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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared production utilities for discovering a GC log's on-disk format
 * (via leading magic bytes) and opening a line-oriented {@link Stream} over
 * plain-text, ZIP-, or GZIP-compressed sources.
 * <p>
 * Both the API module and the parser module previously carried their own
 * copies of this code; this class is the single canonical implementation.
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    /** First magic byte of the two-byte GZIP header. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second magic byte of the two-byte GZIP header. */
    public static final int GZIP_MAGIC2 = 0x8b;

    /** First magic byte of the two-byte ZIP local-file header ('P'). */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second magic byte of the two-byte ZIP local-file header ('K'). */
    public static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSource() {
        // static utility
    }

    /**
     * Discover the on-disk format of {@code path} by inspecting its leading
     * magic bytes. A missing or unreadable file is reported as
     * {@link SourceFormat#PLAINTEXT} to preserve the historical fall-through
     * behaviour of the API module.
     */
    public static SourceFormat detect(Path path) {
        if (path.toFile().isDirectory()) {
            return SourceFormat.DIRECTORY;
        }
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return SourceFormat.GZIP;
        }
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return SourceFormat.ZIP;
        }
        return SourceFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} if the first two bytes of {@code path} match
     * {@code field1} and {@code field2}. A short or unreadable file yields
     * {@code false}.
     */
    public static boolean hasMagic(Path path, int field1, int field2) {
        try (InputStream in = Files.newInputStream(path)) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == field1 && b2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Open a line stream over a plain-text file.
     */
    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over the first non-directory entry of a ZIP file.
     * The stream owns the underlying archive and will release it when closed.
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry = zipStream.getNextEntry();
        while (entry != null && entry.isDirectory()) {
            entry = zipStream.getNextEntry();
        }
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Open a line stream over a GZIP-compressed file. The stream owns the
     * underlying archive and will release it when closed.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to close stream", ioe);
        }
    }
}
