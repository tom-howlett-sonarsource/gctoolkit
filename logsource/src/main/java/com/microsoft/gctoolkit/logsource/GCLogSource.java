// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for working with a GC log source in the file system. A log source is either a
 * directory of log segments or a file that is plain text, ZIP compressed, or GZIP compressed.
 * <p>
 * This is the single home for the behavior that was duplicated between the API and parser
 * modules: discovering the format of a source, reporting the number of bytes it holds, and
 * opening a stream of lines over it.
 */
public final class GCLogSource {

    private static final Logger LOG = Logger.getLogger(GCLogSource.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private GCLogSource() {
    }

    /**
     * Determine the format of the log source found at the given path. A path that cannot be
     * read is reported as {@link LogFileFormat#PLAINTEXT} as there are no magic bytes to say
     * otherwise.
     * @param path The path to the log source.
     * @return The format of the log source.
     */
    public static LogFileFormat discoverFormat(Path path) {
        if (path == null)
            return LogFileFormat.UNKNOWN;
        if (path.toFile().isDirectory())
            return LogFileFormat.DIRECTORY;
        else if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogFileFormat.GZIP;
        else if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogFileFormat.ZIP;
        return LogFileFormat.PLAINTEXT;
    }

    /**
     * Return {@code true} if the first two bytes of the file match the given values.
     * @param path The path to the file.
     * @param first The expected value of the first byte.
     * @param second The expected value of the second byte.
     * @return {@code true} if the file starts with the two given bytes.
     */
    public static boolean hasMagic(Path path, int first, int second) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            return magicByte1 == first && magicByte2 == second;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * Return the number of bytes held by the log source. For a directory, this is the sum of the
     * sizes of the files it contains. Sources that cannot be read are reported as zero bytes.
     * @param path The path to the log source.
     * @return The number of bytes held by the log source.
     */
    public static long sizeInBytes(Path path) {
        if (path == null)
            return 0L;
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> contents = Files.list(path)) {
                    return contents.filter(Files::isRegularFile).mapToLong(GCLogSource::fileSize).sum();
                }
            }
            return fileSize(path);
        } catch (IOException | UncheckedIOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return 0L;
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
            return 0L;
        }
    }

    /**
     * Open a stream of the lines held by the log source, using the given format to decide how the
     * source is to be read.
     * @param path The path to the log source.
     * @param format The format of the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the source cannot be read, including when the format is
     * neither plain text, ZIP, nor GZIP.
     */
    public static Stream<String> stream(Path path, LogFileFormat format) throws IOException {
        if (format != null) {
            switch (format) {
                case PLAINTEXT:
                    return streamPlainText(path);
                case ZIP:
                    return streamZip(path);
                case GZIP:
                    return streamGZip(path);
                default:
                    break;
            }
        }
        throw new IOException("Unable to read " + path);
    }

    /**
     * Open a stream of the lines in an uncompressed log file.
     * @param path The path to the log file.
     * @return A stream of the lines in the log file.
     * @throws IOException Thrown if the file cannot be read.
     */
    public static Stream<String> streamPlainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of the lines in the first non-directory entry of a ZIP compressed file.
     * @param path The path to the ZIP file.
     * @return A stream of the lines in the first entry of the ZIP file.
     * @throws IOException Thrown if the file cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> streamZip(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a stream of the lines in a GZIP compressed file.
     * @param path The path to the GZIP file.
     * @return A stream of the lines in the GZIP file.
     * @throws IOException Thrown if the file cannot be read.
     */
    @SuppressWarnings("resource")
    public static Stream<String> streamGZip(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }
}
