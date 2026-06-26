// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
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
 * Reads the lines of a GC log file, regardless of whether the file is stored as
 * plain text, GZip, or Zip. GCToolKit stores GC logs in all three formats, and
 * the logic for opening each one was previously duplicated across modules; this
 * class is the single home for it.
 * <p>
 * Each method returns a lazily populated {@link Stream}. The underlying file
 * handles are released when the stream is closed, so callers must consume the
 * result in a try-with-resources block:
 * <pre>{@code
 * try (Stream<String> lines = GCLogFileLineSource.gzip(path)) {
 *     ...
 * }
 * }</pre>
 */
public final class GCLogFileLineSource {

    private GCLogFileLineSource() {
    }

    /**
     * Stream the lines of a plain text log file.
     * @param path the path to the log file.
     * @return a stream of the lines in the file.
     * @throws IOException if the file cannot be opened.
     */
    public static Stream<String> plainText(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Stream the lines of a GZip compressed log file.
     * @param path the path to the GZip file.
     * @return a stream of the lines held in the GZip file.
     * @throws IOException if the file cannot be opened or is not valid GZip.
     */
    public static Stream<String> gzip(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return readerLines(new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(new BufferedInputStream(input)), StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            closeAndSuppress(input, e);
            throw e;
        }
    }

    /**
     * Stream the lines of the first non-directory entry of a Zip archive. This
     * mirrors the behaviour expected of a single log file that happens to be
     * stored in a Zip container.
     * @param path the path to the Zip archive.
     * @return a stream of the lines held in the first entry.
     * @throws IOException if the archive cannot be opened or contains no readable entry.
     */
    public static Stream<String> firstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null && entry.isDirectory()) {
                entry = zipStream.getNextEntry();
            }
            if (entry == null) {
                throw new IOException("No readable entry found in " + path);
            }
            return readerLines(new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream), StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            closeAndSuppress(zipStream, e);
            throw e;
        }
    }

    /**
     * Stream the lines of a named entry within a Zip archive.
     * @param path the path to the Zip archive.
     * @param entryName the name of the entry to read.
     * @return a stream of the lines held in the named entry.
     * @throws IOException if the archive cannot be opened or has no such entry.
     */
    public static Stream<String> zipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                throw new IOException("No entry named " + entryName + " in " + path);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8));
            return reader.lines().onClose(closer(zipFile));
        } catch (IOException | RuntimeException e) {
            closeAndSuppress(zipFile, e);
            throw e;
        }
    }

    private static Stream<String> readerLines(BufferedReader reader) {
        return reader.lines().onClose(closer(reader));
    }

    private static Runnable closer(Closeable resource) {
        return () -> {
            try {
                resource.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static void closeAndSuppress(Closeable resource, Exception primary) {
        try {
            resource.close();
        } catch (IOException secondary) {
            primary.addSuppressed(secondary);
        }
    }
}
