// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A logical GC log source, either a file or an entry in an archive.
 */
public final class GCLogSource {

    @FunctionalInterface
    interface InputStreamFactory {
        InputStream open() throws IOException;
    }

    private final Path path;
    private final String name;
    private final long knownByteSize;
    private final InputStreamFactory inputStreamFactory;

    GCLogSource(Path path, String name, long knownByteSize, InputStreamFactory inputStreamFactory) {
        this.path = Objects.requireNonNull(path);
        this.name = Objects.requireNonNull(name);
        this.knownByteSize = knownByteSize;
        this.inputStreamFactory = Objects.requireNonNull(inputStreamFactory);
    }

    public Path path() {
        return path;
    }

    public String name() {
        return name;
    }

    /**
     * Returns the number of uncompressed bytes available from {@link #open()}.
     */
    public long byteSize() throws IOException {
        if (knownByteSize >= 0) {
            return knownByteSize;
        }
        try (InputStream input = open()) {
            long count = 0;
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                count += bytesRead;
            }
            return count;
        }
    }

    public InputStream open() throws IOException {
        return inputStreamFactory.open();
    }

    public Stream<String> lines() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(open(), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }
}
