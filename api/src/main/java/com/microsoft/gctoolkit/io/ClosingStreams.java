// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/**
 * Helpers for producing line streams that release their underlying resources when closed.
 */
final class ClosingStreams {

    private ClosingStreams() {}

    /**
     * Stream the lines of {@code input}. Closing the returned stream closes {@code input} and then {@code owner}.
     * @param input the stream to read lines from
     * @param owner an additional resource (e.g. a {@link java.util.zip.ZipFile}) that owns {@code input}, may be null
     * @return a stream of lines
     */
    static Stream<String> lines(InputStream input, Closeable owner) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        Stream<String> lines = reader.lines().onClose(() -> close(reader));
        return owner == null ? lines : lines.onClose(() -> close(owner));
    }

    static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
