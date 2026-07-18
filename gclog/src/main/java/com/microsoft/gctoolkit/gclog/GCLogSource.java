// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers and opens a plain, ZIP, or GZIP GC log source.
 */
public final class GCLogSource {

    private static final Logger LOGGER = Logger.getLogger(GCLogSource.class.getName());
    private static final int GZIP_MAGIC_BYTE_1 = 0x1F;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8B;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4B;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.format = discoverFormat(path);
    }

    /**
     * Creates a source rooted at {@code path}.
     *
     * @param path source path
     * @return discovered source
     */
    public static GCLogSource from(Path path) {
        return new GCLogSource(path);
    }

    /**
     * Returns the source path.
     *
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the discovered source format.
     *
     * @return source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the number of bytes occupied by the source file.
     * Directories report zero bytes.
     *
     * @return source byte size
     * @throws IOException if the file size cannot be read
     */
    public long byteSize() throws IOException {
        return format == Format.DIRECTORY ? 0L : Files.size(path);
    }

    /**
     * Opens the source as a stream of lines. For ZIP sources, the first
     * non-directory entry is opened.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines();
            case GZIP:
                return gzipLines();
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    private Stream<String> zipLines() throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return readerLines(zipStream);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(zipStream, exception);
            throw exception;
        }
    }

    private Stream<String> gzipLines() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return readerLines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    private static Stream<String> readerLines(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(inputStream)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void closeAfterFailure(InputStream input, Exception originalException) {
        try {
            input.close();
        } catch (IOException closeException) {
            originalException.addSuppressed(closeException);
        }
    }

    private static Format discoverFormat(Path path) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_1 && secondByte == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1 && secondByte == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
        } catch (IOException exception) {
            LOGGER.warning(exception.getMessage());
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
