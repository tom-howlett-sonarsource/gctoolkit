// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A readable file or archive entry containing GC log data.
 */
public final class LogSource {

    private final Path path;
    private final String name;
    private final long size;
    private final String zipEntryName;
    private final LogSources.Format format;

    LogSource(Path path, String name, long size, LogSources.Format format, String zipEntryName) {
        this.path = Objects.requireNonNull(path);
        this.name = Objects.requireNonNull(name);
        this.size = size;
        this.format = Objects.requireNonNull(format);
        this.zipEntryName = zipEntryName;
    }

    /**
     * Returns the filesystem path containing this source.
     * @return source path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the file name or ZIP entry name.
     * @return source name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the source size in bytes. ZIP entry sizes are uncompressed sizes.
     * @return source size in bytes, or {@code -1} when unavailable
     */
    public long size() {
        return size;
    }

    /**
     * Opens the source as a byte stream.
     * @return newly opened stream
     * @throws IOException if the source cannot be opened
     */
    public InputStream openStream() throws IOException {
        return LogSources.open(this);
    }

    /**
     * Opens the source as a stream of lines. Closing the returned stream closes
     * all underlying file and archive resources.
     * @return newly opened line stream
     * @throws IOException if the source cannot be opened
     */
    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(openStream()));
        return reader.lines().onClose(() -> close(reader));
    }

    String zipEntryName() {
        return zipEntryName;
    }

    LogSources.Format format() {
        return format;
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
