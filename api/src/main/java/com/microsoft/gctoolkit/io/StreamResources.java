// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class StreamResources {

    private StreamResources() {
    }

    static <T> Stream<T> closeWith(Stream<T> stream, Closeable resource) {
        return stream.onClose(() -> close(resource));
    }

    private static void close(Closeable resource) {
        try {
            resource.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
