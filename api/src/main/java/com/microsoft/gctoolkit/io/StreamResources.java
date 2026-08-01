// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class StreamResources {

    private StreamResources() {}

    static Stream<String> lines(BufferedReader reader, Closeable... additionalResources) {
        return reader.lines().onClose(() -> close(reader, additionalResources));
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

    private static void close(Closeable reader, Closeable... additionalResources) {
        IOException failure = null;
        try {
            reader.close();
        } catch (IOException closeFailure) {
            failure = closeFailure;
        }

        for (Closeable resource : additionalResources) {
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
