// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Helpers for building line streams whose {@link Stream#close()} releases the underlying resources.
 */
final class LineStreams {

    private LineStreams() {}

    /**
     * Stream the lines of {@code input}. Closing the stream closes {@code input} and then {@code owner}.
     * @param input the stream to read lines from
     * @param owner a resource that owns {@code input} (e.g. a ZipFile), or {@code null}
     * @return a stream of lines
     */
    static Stream<String> lines(InputStream input, Closeable owner) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return reader.lines().onClose(() -> closeAll(reader, owner));
    }

    /**
     * Lazily concatenate the streams produced by {@code mapper}, opening each only once the previous one is
     * exhausted. Closing the returned stream closes the inner stream currently open (if any) and {@code source}.
     */
    static <T, R> Stream<R> concatLazily(Stream<T> source, Function<? super T, Stream<R>> mapper) {
        LazyConcatSpliterator<T, R> spliterator = new LazyConcatSpliterator<>(source.iterator(), mapper);
        return StreamSupport.stream(spliterator, false)
                .onClose(spliterator::close)
                .onClose(source::close);
    }

    private static final class LazyConcatSpliterator<T, R> extends Spliterators.AbstractSpliterator<R> {
        private final Iterator<T> sources;
        private final Function<? super T, Stream<R>> mapper;
        private Stream<R> current;
        private Iterator<R> currentLines = Collections.emptyIterator();

        LazyConcatSpliterator(Iterator<T> sources, Function<? super T, Stream<R>> mapper) {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
            this.sources = sources;
            this.mapper = mapper;
        }

        @Override
        public boolean tryAdvance(Consumer<? super R> action) {
            while (!currentLines.hasNext()) {
                close();
                if (!sources.hasNext())
                    return false;
                current = mapper.apply(sources.next());
                currentLines = current.iterator();
            }
            action.accept(currentLines.next());
            return true;
        }

        void close() {
            if (current != null) {
                Stream<R> toClose = current;
                current = null;
                currentLines = Collections.emptyIterator();
                toClose.close();
            }
        }
    }

    static void closeAll(Closeable... resources) {
        IOException failure = null;
        for (Closeable resource : resources) {
            if (resource == null)
                continue;
            try {
                resource.close();
            } catch (IOException e) {
                if (failure == null)
                    failure = e;
                else
                    failure.addSuppressed(e);
            }
        }
        if (failure != null)
            throw new UncheckedIOException(failure);
    }
}
