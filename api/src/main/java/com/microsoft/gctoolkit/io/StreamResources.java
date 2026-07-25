// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.util.stream.Stream;

final class StreamResources {

    private StreamResources() {
    }

    static <T> Stream<T> closeWith(Stream<T> stream, AutoCloseable... resources) {
        return stream.onClose(() -> close(resources));
    }

    static void close(AutoCloseable... resources) {
        RuntimeException failure = null;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception exception) {
                RuntimeException closeFailure = exception instanceof RuntimeException
                        ? (RuntimeException) exception
                        : new RuntimeException(exception);
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
