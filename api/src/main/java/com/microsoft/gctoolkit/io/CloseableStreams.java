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
        return reader.lines().onClose(() -> close(reader, additionalResources));
    }

    static void closeAfterFailure(Closeable resource, Throwable failure) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    private static void close(Closeable reader, Closeable... additionalResources) {
        IOException failure = closeResource(reader, null);
        for (Closeable resource : additionalResources) {
            failure = closeResource(resource, failure);
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }

    private static IOException closeResource(Closeable resource, IOException failure) {
        try {
            resource.close();
        } catch (IOException exception) {
            if (failure == null) {
                return exception;
            }
            failure.addSuppressed(exception);
        }
        return failure;
    }
}
