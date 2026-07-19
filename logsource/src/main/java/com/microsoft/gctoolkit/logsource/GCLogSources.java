// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared IO utilities for GC log sources.
 *
 * <p>This class centralises the small amount of production behaviour that used
 * to be duplicated between the API and parser modules: detection of the
 * on-disk format from a file's magic bytes, reporting the raw byte size of a
 * file, and opening a line stream over a plain, ZIP, or GZIP source.
 */
public final class GCLogSources {

    private static final Logger LOG = Logger.getLogger(GCLogSources.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSources() {
    }

    /**
     * Classify the source at {@code path} by inspecting either the path (for
     * directories) or the first two bytes of the file (for ZIP and GZIP magic
     * numbers).
     *
     * @param path the file or directory to classify.
     * @return the detected {@link GCLogSourceFormat}; {@link GCLogSourceFormat#PLAINTEXT}
     * when no compressed magic is recognised.
     */
    public static GCLogSourceFormat detectFormat(Path path) {
        if (path.toFile().isDirectory())
            return GCLogSourceFormat.DIRECTORY;
        if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return GCLogSourceFormat.GZIP;
        if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return GCLogSourceFormat.ZIP;
        return GCLogSourceFormat.PLAINTEXT;
    }

    /**
     * Return the size of the file at {@code path} in bytes.
     *
     * @param path the file to measure.
     * @return the size in bytes.
     * @throws IOException if the size cannot be determined.
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open a stream of lines for {@code path} according to its detected
     * {@link GCLogSourceFormat}.
     *
     * @param path the file to open.
     * @return a stream of lines. The caller is responsible for closing the
     * stream.
     * @throws IOException if the file cannot be opened or the format is not
     * recognised.
     */
    public static Stream<String> openLineStream(Path path) throws IOException {
        return openLineStream(path, detectFormat(path));
    }

    /**
     * Open a stream of lines for {@code path} using the supplied
     * {@link GCLogSourceFormat}.
     *
     * @param path the file to open.
     * @param format the format to open the file as.
     * @return a stream of lines. The caller is responsible for closing the
     * stream.
     * @throws IOException if the file cannot be opened or the format is not
     * supported for a single file line stream.
     */
    public static Stream<String> openLineStream(Path path, GCLogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return openPlainStream(path);
            case ZIP:
                return openZipStream(path);
            case GZIP:
                return openGZipStream(path);
            default:
                throw new IOException("Unable to read " + path.toString());
        }
    }

    /**
     * Open a plain text line stream over {@code path}.
     *
     * @param path the file to open.
     * @return a stream of lines.
     * @throws IOException when the file cannot be opened.
     */
    public static Stream<String> openPlainStream(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a line stream over the first non-directory entry of the ZIP file
     * at {@code path}.
     *
     * @param path the ZIP file to open.
     * @return a stream of lines.
     * @throws IOException when the file cannot be opened.
     */
    public static Stream<String> openZipStream(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a line stream over the GZIP file at {@code path}.
     *
     * @param path the GZIP file to open.
     * @return a stream of lines.
     * @throws IOException when the file cannot be opened.
     */
    public static Stream<String> openGZipStream(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    private static boolean hasMagic(Path path, int field1, int field2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == field1 && magicByte2 == field2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }
}
