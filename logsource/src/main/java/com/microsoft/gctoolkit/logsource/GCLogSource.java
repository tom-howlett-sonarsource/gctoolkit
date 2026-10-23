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
 * Shared primitives for working with GC log sources. These utilities are used by both the API and
 * parser modules to discover the format of a log source, sniff its leading (magic) bytes, and open
 * plain text, ZIP, and GZIP log streams, avoiding duplicated IO behavior across modules.
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    /** First magic byte of a GZIP stream. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second magic byte of a GZIP stream. */
    public static final int GZIP_MAGIC2 = 0x8b;

    /** First magic byte of a ZIP archive. */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second magic byte of a ZIP archive. */
    public static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Discover the format of the source at the given path by inspecting the file system and the
     * source's leading (magic) bytes.
     * @param path The path to the source.
     * @return The discovered {@link LogFileFormat}.
     */
    public static LogFileFormat discoverFormat(Path path) {
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        else if (hasMagicBytes(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        else if (hasMagicBytes(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        else
            return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} if the first two bytes of the source match the given signature.
     * @param path The path to the source.
     * @param first The expected value of the first byte.
     * @param second The expected value of the second byte.
     * @return {@code true} if the leading bytes match the signature.
     */
    public static boolean hasMagicBytes(Path path, int first, int second) {
        try (FileInputStream magicByteReader = new FileInputStream(path.toFile())) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == first && magicByte2 == second;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Open a plain text log source as a stream of lines.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be opened.
     */
    public static Stream<String> openPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry of a ZIP source as a stream of lines.
     * @param path The path to the source.
     * @return A stream of the lines in the first ZIP entry.
     * @throws IOException if the source cannot be opened.
     */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP source as a stream of lines.
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException if the source cannot be opened.
     */
    public static Stream<String> openGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
