// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class ResourceStreams {

    private ResourceStreams() {
    }

    static <T> Stream<T> onClose(Stream<T> stream, Closeable... resources) {
        return stream.onClose(() -> close(resources));
    }

    static Stream<String> lines(BufferedReader reader, Closeable... additionalResources) {
        Closeable[] resources = new Closeable[additionalResources.length + 1];
        resources[0] = reader;
        System.arraycopy(additionalResources, 0, resources, 1, additionalResources.length);
        return onClose(reader.lines(), resources);
    }

    static void closeAfterFailure(Throwable failure, Closeable... resources) {
        for (Closeable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static void close(Closeable... resources) {
        IOException failure = null;
        for (Closeable resource : resources) {
            if (resource == null) {
                continue;
            }
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
