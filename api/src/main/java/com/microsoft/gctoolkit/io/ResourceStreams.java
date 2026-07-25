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

    static Stream<String> lines(BufferedReader reader, Closeable... additionalResources) {
        return reader.lines().onClose(() -> close(reader, additionalResources));
    }

    static void closeAfterFailure(Throwable failure, Closeable... resources) {
        try {
            close(resources);
        } catch (UncheckedIOException closeException) {
            failure.addSuppressed(closeException.getCause());
        }
    }

    private static void close(Closeable firstResource, Closeable... additionalResources) {
        IOException exception = closeResource(firstResource, null);
        for (Closeable resource : additionalResources) {
            exception = closeResource(resource, exception);
        }
        if (exception != null) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void close(Closeable... resources) {
        IOException exception = null;
        for (Closeable resource : resources) {
            exception = closeResource(resource, exception);
        }
        if (exception != null) {
            throw new UncheckedIOException(exception);
        }
    }

    private static IOException closeResource(Closeable resource, IOException exception) {
        if (resource == null) {
            return exception;
        }
        try {
            resource.close();
        } catch (IOException closeException) {
            if (exception == null) {
                return closeException;
            }
            exception.addSuppressed(closeException);
        }
        return exception;
    }
}
