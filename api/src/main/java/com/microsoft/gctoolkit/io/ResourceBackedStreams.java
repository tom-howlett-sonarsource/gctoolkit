package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class ResourceBackedStreams {

    private ResourceBackedStreams() {
    }

    static Stream<String> lines(BufferedReader reader, Closeable... additionalResources) {
        Stream<String> lines = reader.lines().onClose(closeHandler(reader));
        for (Closeable resource : additionalResources) {
            lines = lines.onClose(closeHandler(resource));
        }
        return lines;
    }

    static void closeAfterFailure(Closeable resource, Exception failure) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    private static Runnable closeHandler(Closeable resource) {
        return () -> {
            try {
                resource.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
    }
}
