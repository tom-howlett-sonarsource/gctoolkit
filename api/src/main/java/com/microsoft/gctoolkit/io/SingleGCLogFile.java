// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A single GC log file. If the file is a zip or gzip file,
 * then the first entry is the file of interest.
 */
public class SingleGCLogFile extends GCLogFile {

    private static final Logger LOGGER = Logger.getLogger(SingleGCLogFile.class.getName());

    /**
     * Constructor for a single, GC log file.
     * @param path The path to the log file.
     */

    private SingleLogFileMetadata metadata = null;

    public SingleGCLogFile(Path path) {
        super(path);
    }

    @Override
    public LogFileMetadata getMetaData() throws IOException {
        if (metadata == null) {
            metadata = new SingleLogFileMetadata(path);
        }
        return metadata;
    }

    @Override
    public Stream<String> stream() throws IOException {
        return stream(getMetaData());
    }

    private Stream<String> stream(LogFileMetadata metadata) throws IOException {
        Stream<String> stream = null;
        if (metadata.isPlainText()) {
            stream = Files.lines(metadata.getPath());
        } else if (metadata.isZip()) {
            stream = streamZipFile(metadata.getPath());
        } else if (metadata.isGZip()) {
            stream = streamGZipFile(metadata.getPath());
        }
        if ( stream == null)
            throw new IOException("Unable to read " + path.toString());
        return Stream.concat(stream
                .filter(Objects::nonNull)
                .filter(line -> ! line.isBlank())
                .map(String::trim)
                .filter(s -> s.length() > 0)
                ,Stream.of(endOfData()));

    }

    private static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
        } catch (IOException | RuntimeException e) {
            closeQuietly(zipStream);
            throw e;
        }
        return linesClosingOnClose(new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream))), zipStream);
    }

    private static Stream<String> streamGZipFile(Path path) throws IOException {
        InputStream fileStream = Files.newInputStream(path);
        GZIPInputStream gzipStream;
        try {
            gzipStream = new GZIPInputStream(fileStream);
        } catch (IOException | RuntimeException e) {
            closeQuietly(fileStream);
            throw e;
        }
        return linesClosingOnClose(new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream))), gzipStream);
    }

    /**
     * Stream the lines of {@code reader}, closing both the reader and {@code resource} when the stream is closed.
     */
    static Stream<String> linesClosingOnClose(BufferedReader reader, Closeable resource) {
        return reader.lines().onClose(() -> {
            try (Closeable r = resource; BufferedReader b = reader) {
                // resources are closed by try-with-resources
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    static void closeQuietly(Closeable resource) {
        if (resource == null)
            return;
        try {
            resource.close();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to close resource", e);
        }
    }

}
