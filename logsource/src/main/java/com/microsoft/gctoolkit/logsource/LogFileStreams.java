// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Opens GC log sources as a stream of lines. The stream holds the underlying file open, so
 * callers should close the stream, either directly or with a try-with-resources block, when
 * they are done reading.
 */
public final class LogFileStreams {

    private LogFileStreams() {
        // static utilities only
    }

    /**
     * Stream the lines of a source that is held in the given format.
     *
     * @param path The path to the source.
     * @param format The format of the source, as reported by {@link LogFileFormat#detect(Path)}.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read, or is held in a format that
     * cannot be streamed as a single log.
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
                throw new IOException("Unable to read " + path.toString());
        }
    }

    /**
     * Stream the lines of an uncompressed source.
     *
     * @param path The path to the source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the lines of the first, non directory entry of a ZIP source.
     *
     * @param path The path to the ZIP source.
     * @return A stream of the lines in the first entry of the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(zipStream);
        } catch (IOException | RuntimeException e) {
            close(zipStream);
            throw e;
        }
    }

    /**
     * Stream the lines of a named entry of a ZIP source.
     *
     * @param path The path to the ZIP source.
     * @param entryName The name of the entry to be streamed.
     * @return A stream of the lines in the named entry.
     * @throws IOException Thrown if the source cannot be read, or does not hold the entry.
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException(path + " does not contain " + entryName);
            return lines(zipFile.getInputStream(entry)).onClose(() -> close(zipFile));
        } catch (IOException | RuntimeException e) {
            close(zipFile);
            throw e;
        }
    }

    /**
     * Stream the lines of a GZIP compressed source.
     *
     * @param path The path to the GZIP source.
     * @return A stream of the lines in the source.
     * @throws IOException Thrown if the source cannot be read.
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        InputStream source = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(source));
        } catch (IOException | RuntimeException e) {
            close(source);
            throw e;
        }
    }

    private static Stream<String> lines(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(inputStream)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
}
