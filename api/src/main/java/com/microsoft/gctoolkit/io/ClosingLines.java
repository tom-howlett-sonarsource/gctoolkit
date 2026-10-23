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
 * Builds line streams that release their underlying resources when the stream is closed.
 */
final class ClosingLines {

    private ClosingLines() {}

    /**
     * Stream the lines of {@code input}, closing {@code input} and {@code owner} when the stream is closed.
     * @param input the stream to read lines from.
     * @param owner an additional resource owning {@code input} (for example a {@code ZipFile}), may be {@code input}.
     * @return a stream of lines that closes all resources on {@link Stream#close()}.
     */
    static Stream<String> of(InputStream input, Closeable owner) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return reader.lines().onClose(() -> {
            try (Closeable o = owner; BufferedReader r = reader) {
                // closed by try-with-resources
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Close {@code resource} after a failure, attaching any secondary failure to {@code failure}.
     */
    static <T extends Throwable> T closeOnFailure(Closeable resource, T failure) {
        try {
            resource.close();
        } catch (IOException e) {
            failure.addSuppressed(e);
        }
        return failure;
    }
}
