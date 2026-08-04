// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * A filesystem source containing GC log data.
 *
 * <p>The source type is discovered from the file's magic bytes rather than its
 * name. ZIP sources expose the first non-directory entry by default, matching
 * the behavior of a single GC log source.</p>
 */
public final class GCLogSource {

    private static final int GZIP_MAGIC_1 = 0x1f;
    private static final int GZIP_MAGIC_2 = 0x8b;
    private static final int ZIP_MAGIC_1 = 0x50;
    private static final int ZIP_MAGIC_2 = 0x4b;
    private static final int BUFFER_SIZE = 8192;

    private final Path path;
    private final Type type;

    private GCLogSource(Path path, Type type) {
        this.path = path;
        this.type = type;
    }

    /**
     * Discover the type of a log source.
     *
     * @param path source path
     * @return the discovered source
     * @throws IOException if the source cannot be inspected
     */
    public static GCLogSource from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return new GCLogSource(path, Type.DIRECTORY);
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                return new GCLogSource(path, Type.GZIP);
            }
            if (first == ZIP_MAGIC_1 && second == ZIP_MAGIC_2) {
                return new GCLogSource(path, Type.ZIP);
            }
            return new GCLogSource(path, Type.PLAIN_TEXT);
        }
    }

    /**
     * Return the source path.
     *
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return the discovered source type.
     *
     * @return source type
     */
    public Type getType() {
        return type;
    }

    /**
     * Return the source's size on disk.
     *
     * @return number of bytes occupied by the source file
     * @throws IOException if its attributes cannot be read
     */
    public long byteSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Return the number of uncompressed bytes exposed by {@link #openStream()}.
     *
     * @return number of logical content bytes
     * @throws IOException if the source cannot be read
     */
    public long contentByteSize() throws IOException {
        if (type == Type.DIRECTORY) {
            long size = 0L;
            for (Path source : discover(path)) {
                if (Files.isRegularFile(source)) {
                    size += Files.size(source);
                }
            }
            return size;
        }
        try (InputStream input = openStream()) {
            return countBytes(input);
        }
    }

    /**
     * Discover the direct children of a directory.
     *
     * @param directory directory to inspect
     * @return children in filesystem encounter order
     * @throws IOException if the directory cannot be listed
     */
    public static List<Path> discover(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    /**
     * Return the file entry names in a ZIP source.
     *
     * @return non-directory ZIP entry names
     * @throws IOException if this is not a ZIP source or it cannot be read
     */
    public List<String> entries() throws IOException {
        requireZip();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open the source's logical content. For a ZIP this is the first
     * non-directory entry.
     *
     * @return input stream owned by the caller
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        switch (type) {
            case PLAIN_TEXT:
                return new BufferedInputStream(Files.newInputStream(path));
            case GZIP:
                return new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path)));
            case ZIP:
                return openFirstZipEntry();
            case DIRECTORY:
            default:
                throw new IOException("Unable to open directory as a log stream: " + path);
        }
    }

    /**
     * Open a named file entry in a ZIP source.
     *
     * @param entryName ZIP entry name
     * @return input stream owned by the caller
     * @throws IOException if the source or entry cannot be opened
     */
    public InputStream openStream(String entryName) throws IOException {
        requireZip();
        Objects.requireNonNull(entryName, "entryName");

        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    return zip;
                }
            }
        } catch (IOException exception) {
            zip.close();
            throw exception;
        }
        zip.close();
        throw new IOException("ZIP entry not found: " + entryName);
    }

    /**
     * Stream UTF-8 lines from the source's logical content.
     *
     * @return closeable line stream
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        return lines(openStream());
    }

    /**
     * Stream UTF-8 lines from a named ZIP entry.
     *
     * @param entryName ZIP entry name
     * @return closeable line stream
     * @throws IOException if the source or entry cannot be opened
     */
    public Stream<String> lines(String entryName) throws IOException {
        return lines(openStream(entryName));
    }

    private InputStream openFirstZipEntry() throws IOException {
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    return zip;
                }
            }
            return zip;
        } catch (IOException exception) {
            zip.close();
            throw exception;
        }
    }

    private void requireZip() throws IOException {
        if (type != Type.ZIP) {
            throw new IOException("Not a ZIP log source: " + path);
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static long countBytes(InputStream input) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long count = 0L;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            count += bytesRead;
        }
        return count;
    }

    /**
     * Supported GC log source types.
     */
    public enum Type {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }
}
