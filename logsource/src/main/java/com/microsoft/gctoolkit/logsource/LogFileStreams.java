// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Opens streams of log lines over the plain text, ZIP and GZIP forms of a GC log source.
 */
public final class LogFileStreams {

    private LogFileStreams() {}

    /**
     * Open a stream of log lines over a log source of the given format.
     * @param path The path to the log source.
     * @param format The format of the log source.
     * @return A stream of the lines in the log source.
     * @throws IOException Thrown if the log source cannot be read, including when the format
     * cannot be streamed as a single log file.
     */
    public static Stream<String> lines(Path path, LogFileFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return plainTextLines(path);
            case ZIP:
                return firstZipEntryLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Open a stream of log lines over an uncompressed log file.
     * @param path The path to the log file.
     * @return A stream of the lines in the log file.
     * @throws IOException Thrown if the log file cannot be read.
     */
    public static Stream<String> plainTextLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open a stream of log lines over the first log file held in a ZIP archive.
     * @param path The path to the ZIP archive.
     * @return A stream of the lines in the first log file in the archive.
     * @throws IOException Thrown if the archive cannot be read.
     */
    public static Stream<String> firstZipEntryLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(zipStream);
        } catch (IOException ioe) {
            zipStream.close();
            throw ioe;
        }
    }

    /**
     * Open a stream of log lines over a named log file held in a ZIP archive. The archive is
     * released when the returned stream is closed.
     * @param path The path to the ZIP archive.
     * @param entryName The name of the log file in the archive.
     * @return A stream of the lines in the named log file.
     * @throws IOException Thrown if the archive cannot be read or does not hold the named entry.
     */
    public static Stream<String> zipEntryLines(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new IOException("Unable to find " + entryName + " in " + path);
            return closeWithStream(zipFile, lines(zipFile.getInputStream(entry)));
        } catch (IOException ioe) {
            zipFile.close();
            throw ioe;
        }
    }

    /**
     * Open a stream of log lines over a GZip compressed log file.
     * @param path The path to the compressed log file.
     * @return A stream of the lines in the log file.
     * @throws IOException Thrown if the log file cannot be read.
     */
    public static Stream<String> gzipLines(Path path) throws IOException {
        InputStream compressed = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(compressed));
        } catch (IOException ioe) {
            compressed.close();
            throw ioe;
        }
    }

    /**
     * Read lines from the given stream, closing it when the returned stream of lines is closed.
     */
    private static Stream<String> lines(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        return closeWithStream(reader, reader.lines());
    }

    private static Stream<String> closeWithStream(Closeable resource, Stream<String> lines) {
        return lines.onClose(() -> {
            try {
                resource.close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
    }
}
