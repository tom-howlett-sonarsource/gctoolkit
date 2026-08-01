// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Opens GC log sources as a stream of lines. Plain text, ZIP and GZIP sources are all supported.
 * <p>
 * The returned streams are lazy and hold the underlying file handles open. Closing the stream
 * closes everything that was opened to produce it, so callers should use them in a
 * try-with-resources block.
 */
public final class LogFileStreams {

    private static final Logger LOG = Logger.getLogger(LogFileStreams.class.getName());

    private static final String UNABLE_TO_READ = "Unable to read ";

    private LogFileStreams() {
    }

    /**
     * Open the source at the given path, discovering its format first.
     *
     * @param path The path to the log source.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, LogFileFormat.discover(path));
    }

    /**
     * Open the source at the given path, using the format that has already been discovered for it.
     *
     * @param path The path to the log source.
     * @param format The format of the log source.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read, or the format cannot be streamed.
     */
    public static Stream<String> lines(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return plainTextLines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException(UNABLE_TO_READ + path);
        }
    }

    /**
     * Open an uncompressed source.
     *
     * @param path The path to the log source.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first entry of a ZIP archive that is not a directory.
     *
     * @param path The path to the ZIP archive.
     * @return A stream of the lines in the first entry of the archive.
     * @throws IOException If the archive cannot be read.
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return linesFrom(zipStream);
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipStream);
            throw e;
        }
    }

    /**
     * Open a named entry of a ZIP archive.
     *
     * @param path The path to the ZIP archive.
     * @param entryName The name of the entry within the archive.
     * @return A stream of the lines in that entry.
     * @throws IOException If the archive cannot be read, or it holds no such entry.
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException(UNABLE_TO_READ + entryName + " in " + path);
            return linesFrom(zipFile.getInputStream(entry)).onClose(() -> closeQuietly(zipFile));
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipFile);
            throw e;
        }
    }

    /**
     * Open every entry of a ZIP archive that is not a directory, as a single stream of lines
     * running from the first entry through to the last.
     *
     * @param path The path to the ZIP archive.
     * @return A stream of the lines in all of the entries of the archive.
     * @throws IOException If the archive cannot be read.
     */
    public static Stream<String> allZipEntryLines(Path path) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            List<InputStream> streams = new ArrayList<>();
            for (ZipEntry entry : entriesOf(zipFile))
                streams.add(zipFile.getInputStream(entry));
            return linesFrom(new SequenceInputStream(Collections.enumeration(streams)))
                    .onClose(() -> closeQuietly(zipFile));
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipFile);
            throw e;
        }
    }

    /**
     * Open a GZIP compressed source.
     *
     * @param path The path to the log source.
     * @return A stream of the lines in the source.
     * @throws IOException If the source cannot be read.
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        try {
            return linesFrom(gzipStream);
        } catch (RuntimeException e) {
            closeQuietly(gzipStream);
            throw e;
        }
    }

    private static List<ZipEntry> entriesOf(ZipFile zipFile) {
        List<ZipEntry> entries = new ArrayList<>();
        zipFile.stream().filter(entry -> !entry.isDirectory()).forEach(entries::add);
        return entries;
    }

    private static Stream<String> linesFrom(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to close log source", ioe);
        }
    }
}
