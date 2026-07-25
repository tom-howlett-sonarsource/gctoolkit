// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class CloseableStream {

    private CloseableStream() {
    }

    static <T> Stream<T> onClose(Stream<T> stream, Closeable... resources) {
        return stream.onClose(() -> close(resources));
    }

    private static void close(Closeable... resources) {
        UncheckedIOException failure = null;
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = new UncheckedIOException(exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
