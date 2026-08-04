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

    static <T> Stream<T> closeWith(Stream<T> stream, Closeable... resources) {
        return stream.onClose(() -> close(resources));
    }

    static void closeAfterFailure(Throwable failure, Closeable... resources) {
        try {
            close(resources);
        } catch (UncheckedIOException closeFailure) {
            failure.addSuppressed(closeFailure.getCause());
        }
    }

    private static void close(Closeable... resources) {
        IOException failure = null;
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }
}
