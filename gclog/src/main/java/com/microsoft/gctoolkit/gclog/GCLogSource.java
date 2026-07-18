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
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * A file-system source containing a plain, ZIP, or GZIP GC log.
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;
    private static final int ZIP_MAGIC_BYTE_1 = 0x50;
    private static final int ZIP_MAGIC_BYTE_2 = 0x4b;

    private final Path path;
    private final Format format;

    private GCLogSource(Path path) {
        this.path = Objects.requireNonNull(path);
        this.format = discoverFormat(path);
    }

    /**
     * Discovers the source format from the path and file contents.
     *
     * @param path source path
     * @return the discovered source
     */
    public static GCLogSource from(Path path) {
        return new GCLogSource(path);
    }

    /**
     * @return source path
     */
    public Path path() {
        return path;
    }

    /**
     * @return discovered source format
     */
    public Format format() {
        return format;
    }

    /**
     * Returns the number of bytes occupied by the source file.
     *
     * @return source size in bytes
     * @throws IOException if the size cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Lists non-directory entries in a ZIP source.
     *
     * @return ZIP entry names in archive order
     * @throws IOException if the archive cannot be read
     */
    public List<String> entries() throws IOException {
        if (format != Format.ZIP) {
            return List.of();
        }
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Opens lines from a plain source, a GZIP source, or the first file in a ZIP source.
     * Closing the returned stream closes the underlying source.
     *
     * @return source lines
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> open() throws IOException {
        switch (format) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openZipEntry(entry -> true);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens a named file entry in a ZIP source.
     * Closing the returned stream closes the underlying archive.
     *
     * @param entryName ZIP entry name
     * @return entry lines
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> open(String entryName) throws IOException {
        Objects.requireNonNull(entryName);
        if (format != Format.ZIP) {
            throw new IOException("Unable to read ZIP entry from " + path);
        }
        return openZipEntry(entry -> entryName.equals(entry.getName()));
    }

    private Stream<String> openZipEntry(Predicate<ZipEntry> selector) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && selector.test(entry)) {
                    return lines(zipStream);
                }
            }
            zipStream.close();
            return Stream.empty();
        } catch (IOException | RuntimeException exception) {
            try {
                zipStream.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream inputStream) {
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

    private static Format discoverFormat(Path path) {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            int firstByte = inputStream.read();
            int secondByte = inputStream.read();
            if (firstByte == GZIP_MAGIC_BYTE_1 && secondByte == GZIP_MAGIC_BYTE_2) {
                return Format.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_1 && secondByte == ZIP_MAGIC_BYTE_2) {
                return Format.ZIP;
            }
        } catch (IOException ignored) {
            return Format.PLAIN_TEXT;
        }
        return Format.PLAIN_TEXT;
    }

    /**
     * Supported source formats.
     */
    public enum Format {
        ZIP,
        GZIP,
        PLAIN_TEXT,
        DIRECTORY
    }
}
