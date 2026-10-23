// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Helpers that tie the lifetime of underlying I/O resources to the {@link Stream} built on top of them.
 */
final class StreamResources {

    private StreamResources() {}

    /**
     * Stream the lines of {@code reader}; closing the stream closes {@code reader} and then {@code resource}.
     */
    static Stream<String> lines(BufferedReader reader, Closeable resource) {
        return reader.lines().onClose(() -> closeAll(List.of(reader, resource)));
    }

    /**
     * Like {@link Stream#flatMap}, but every inner stream opened is also closed when the returned stream
     * is closed, even if the inner stream was only partially consumed.
     */
    static <T, R> Stream<R> flatMapClosing(Stream<T> source, Function<? super T, ? extends Stream<? extends R>> mapper) {
        List<Stream<? extends R>> opened = new ArrayList<>();
        return source
                .<R>flatMap(element -> {
                    Stream<? extends R> inner = mapper.apply(element);
                    synchronized (opened) {
                        opened.add(inner);
                    }
                    return inner;
                })
                .onClose(() -> {
                    synchronized (opened) {
                        closeAll(opened);
                        opened.clear();
                    }
                });
    }

    private static void closeAll(List<? extends AutoCloseable> resources) {
        RuntimeException failure = null;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                RuntimeException wrapped = e instanceof RuntimeException ? (RuntimeException) e
                        : new UncheckedIOException(e instanceof IOException ? (IOException) e : new IOException(e));
                if (failure == null)
                    failure = wrapped;
                else
                    failure.addSuppressed(wrapped);
            }
        }
        if (failure != null)
            throw failure;
    }
}
