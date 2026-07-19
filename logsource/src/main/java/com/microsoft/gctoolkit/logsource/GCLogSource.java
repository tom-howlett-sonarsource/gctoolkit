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
 * Shared, low-level utilities for GC log source discovery and stream opening.
 * <p>
 * Every GC log source is either a directory, a plain-text file, a ZIP archive,
 * or a GZIP-compressed file. This class knows how to tell them apart (by reading
 * two magic bytes) and how to open each variant as a {@link Stream} of lines,
 * without imposing any filtering or line-processing policy of its own.
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    /** First magic byte of a GZIP-compressed file. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second magic byte of a GZIP-compressed file. */
    public static final int GZIP_MAGIC2 = 0x8B;

    /** First magic byte of a ZIP archive ('P'). */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second magic byte of a ZIP archive ('K'). */
    public static final int ZIP_MAGIC2 = 0x4B;

    private GCLogSource() {
        // no instances
    }

    /**
     * Detect the {@link GCLogFileFormat} of a source on disk by looking at its
     * type (directory vs regular file) and, for regular files, the first two
     * bytes on disk.
     * @param path the path to inspect; must not be {@code null}.
     * @return the detected format; {@link GCLogFileFormat#PLAINTEXT} when the
     *   file exists but does not begin with a known magic sequence.
     */
    public static GCLogFileFormat detect(Path path) {
        if (path.toFile().isDirectory())
            return GCLogFileFormat.DIRECTORY;
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return GCLogFileFormat.GZIP;
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return GCLogFileFormat.ZIP;
        return GCLogFileFormat.PLAINTEXT;
    }

    /**
     * Read the first two bytes of the given file and return {@code true} iff
     * they match the supplied magic values. If the file cannot be opened the
     * failure is logged at WARNING and {@code false} is returned.
     * @param path the file to sample; must not be {@code null}.
     * @param magic1 expected first byte
     * @param magic2 expected second byte
     * @return {@code true} iff the file's first two bytes match.
     */
    public static boolean matchesMagic(Path path, int magic1, int magic2) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == magic1 && magicByte2 == magic2;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Open a plain-text log file as a stream of lines.
     * @param path the file to open.
     * @return a stream of lines from the file.
     * @throws IOException if the file cannot be opened.
     */
    public static Stream<String> streamPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry of a ZIP archive as a stream of lines.
     * @param path the archive to open.
     * @return a stream of lines from the first regular entry.
     * @throws IOException if the archive cannot be opened.
     */
    @SuppressWarnings({"java:S2095", "resource"})
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    /**
     * Open a GZIP-compressed log file as a stream of lines.
     * @param path the file to open.
     * @return a stream of lines.
     * @throws IOException if the file cannot be opened.
     */
    @SuppressWarnings({"java:S2095", "resource"})
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
    }
}
