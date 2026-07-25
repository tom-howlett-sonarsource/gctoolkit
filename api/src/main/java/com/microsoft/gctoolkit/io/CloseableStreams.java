// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class CloseableStreams {

    private CloseableStreams() {
    }

    static Stream<String> lines(BufferedReader reader, Closeable... resources) {
        return reader.lines().onClose(() -> close(resources));
    }

    private static void close(Closeable... resources) {
        IOException failure = null;
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }
}
