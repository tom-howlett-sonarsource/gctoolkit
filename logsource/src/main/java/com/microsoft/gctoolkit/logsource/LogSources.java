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
 * Discovers, sizes and opens GC log sources. If the source is a zip or gzip file,
 * the first file entry is the content of interest.
 */
public final class LogSources {

    private static final Logger LOG = Logger.getLogger(LogSources.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSources() {}

    /**
     * Determine the format of the source from its magic bytes.
     * An unreadable file is treated as plain text.
     * @param path the source
     * @return the format of the source
     */
    public static LogSourceFormat discover(Path path) {
        if (Files.isDirectory(path))
            return LogSourceFormat.DIRECTORY;
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            int magicByte1 = magicByteReader.read();
            int magicByte2 = magicByteReader.read();
            if (magicByte1 == GZIP_MAGIC1 && magicByte2 == GZIP_MAGIC2)
                return LogSourceFormat.GZIP;
            if (magicByte1 == ZIP_MAGIC1 && magicByte2 == ZIP_MAGIC2)
                return LogSourceFormat.ZIP;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return LogSourceFormat.PLAINTEXT;
    }

    /**
     * @param path the source
     * @return the size of the source in bytes, as stored on disk
     * @throws IOException if the size cannot be read
     */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Open the content of the source, decompressing it if required.
     * @param path the source
     * @param format the format of the source
     * @return a stream of the (decompressed) content, which the caller must close
     * @throws IOException if the source cannot be opened
     */
    public static InputStream open(Path path, LogSourceFormat format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            case GZIP:
                return new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)));
            case ZIP:
                return new BufferedInputStream(openFirstZipEntry(path));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Stream the lines of the source, decompressing it if required.
     * Closing the returned stream releases the underlying file.
     * @param path the source
     * @param format the format of the source
     * @return the lines of the source
     * @throws IOException if the source cannot be opened
     */
    public static Stream<String> lines(Path path, LogSourceFormat format) throws IOException {
        if (format == LogSourceFormat.PLAINTEXT)
            return Files.lines(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(open(path, format)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
    }

    private static ZipInputStream openFirstZipEntry(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return zipStream;
        } catch (IOException ioe) {
            zipStream.close();
            throw ioe;
        }
    }
}
