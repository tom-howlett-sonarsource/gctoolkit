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

    static Stream<String> lines(BufferedReader reader, Closeable... additionalResources) {
        Stream<String> lines = reader.lines().onClose(close(reader));
        for (Closeable resource : additionalResources) {
            lines = lines.onClose(close(resource));
        }
        return lines;
    }

    static void closeAfterFailure(Closeable resource, Throwable failure) {
        try {
            resource.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static Runnable close(Closeable resource) {
        return () -> {
            try {
                resource.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
    }
}
