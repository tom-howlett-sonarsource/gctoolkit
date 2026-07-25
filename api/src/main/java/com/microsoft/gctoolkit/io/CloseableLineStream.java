package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class CloseableLineStream {

    private CloseableLineStream() {
    }

    static Stream<String> lines(BufferedReader reader) {
        return reader.lines().onClose(() -> close(reader));
    }

    static void close(Closeable resource) {
        try {
            resource.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
