// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class ResourceClosingStreams {

    private ResourceClosingStreams() {
    }

    static Stream<String> lines(final BufferedReader reader,
                                final Closeable... additionalResources) {
        Stream<String> lines = reader.lines().onClose(() -> close(reader));
        for (Closeable resource : additionalResources) {
            lines = lines.onClose(() -> close(resource));
        }
        return lines;
    }

    static void closeAfterFailure(final Throwable failure,
                                  final Closeable... resources) {
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static void close(final Closeable resource) {
        try {
            resource.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
