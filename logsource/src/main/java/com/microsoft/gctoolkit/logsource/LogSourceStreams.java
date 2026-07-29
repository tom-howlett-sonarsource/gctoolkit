// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Opens a GC log source as a stream of the lines it holds. Ownership of the source is handed to
 * the returned stream, in the manner of {@link Files#lines(Path)}: the source stays open for as
 * long as the stream is being read and is released when the stream is closed. The source cannot
 * be closed before the stream is returned, hence the suppression of the resource warnings below.
 */
public final class LogSourceStreams {

    private static final Logger LOG = Logger.getLogger(LogSourceStreams.class.getName());

    private LogSourceStreams() {
    }

    /**
     * Open the log source at the given path, discovering its format.
     * @param path The path to the log source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, LogSourceFormat.of(path));
    }

    /**
     * Open the log source at the given path, which is known to be in the given format.
     * @param path The path to the log source.
     * @param format The format of the log source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read, or is in a format that
     * cannot be streamed.
     */
    public static Stream<String> lines(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return plainTextLines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open an uncompressed log source.
     * @param path The path to the log source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a named entry of a Zip compressed log source.
     * @param path The path to the Zip file.
     * @param entryName The name of the entry to be read, as reported by
     * {@link LogSourceFiles#zipEntryNames(Path)}.
     * @return A stream of the lines in the entry.
     * @throws IOException Thrown if the Zip file cannot be read, or holds no such entry.
     */
    @SuppressWarnings({"resource", "java:S2095"}) // the returned stream closes the Zip file
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("Unable to find " + entryName + " in " + path);
            return bufferedLines(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException e) {
            close(zipFile);
            throw e;
        }
    }

    /**
     * Open the first file entry of a Zip compressed log source.
     */
    @SuppressWarnings({"resource", "java:S2095"}) // the returned stream closes the Zip stream
    private static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        return bufferedLines(zipStream);
    }

    private static Stream<String> gzipLines(Path path) throws IOException {
        return bufferedLines(new GZIPInputStream(Files.newInputStream(path)));
    }

    private static Stream<String> bufferedLines(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(inputStream), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
    }
}
