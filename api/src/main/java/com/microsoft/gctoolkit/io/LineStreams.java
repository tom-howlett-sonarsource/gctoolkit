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
 * Creates line streams that release their underlying resources when the stream is closed.
 */
final class LineStreams {

    private LineStreams() {}

    /**
     * Stream the lines of {@code input}. Closing the returned stream closes the reader, {@code input}
     * and every {@code additional} resource (for example, the owning archive).
     * @param input the source of the lines
     * @param additional extra resources to close, in order, after {@code input}
     * @return a stream of lines that owns {@code input} and {@code additional}
     */
    static Stream<String> lines(InputStream input, Closeable... additional) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return reader.lines().onClose(() -> {
            try {
                closeAll(reader, additional);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Close {@code first} and then every {@code others}, even when a close fails.
     * The first failure is thrown with subsequent failures suppressed.
     * @param first the first resource to close
     * @param others further resources to close
     * @throws IOException if any resource failed to close
     */
    static void closeAll(Closeable first, Closeable... others) throws IOException {
        IOException failure = null;
        Closeable[] all = new Closeable[others.length + 1];
        all[0] = first;
        System.arraycopy(others, 0, all, 1, others.length);
        for (Closeable resource : all) {
            try {
                resource.close();
            } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }
}
