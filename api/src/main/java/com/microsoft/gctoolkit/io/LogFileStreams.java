// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Ties the lifecycle of a stream of lines to the resources the lines are read from.
 * {@link BufferedReader#lines()} does not close its reader, so an archive, a channel or a
 * reader opened to produce the lines has to be released explicitly when the stream is closed.
 */
final class LogFileStreams {

    private static final Logger LOGGER = Logger.getLogger(LogFileStreams.class.getName());

    private LogFileStreams() {
    }

    /**
     * Stream the lines read from {@code source}. Closing the returned stream closes the reader
     * created here and then, in the order given, each of {@code alsoClose}. Closing the stream
     * therefore releases the whole chain, down to and including the archive the bytes came from.
     * @param source The bytes to read the lines from. The returned stream owns it.
     * @param alsoClose Resources to release, after the reader, when the stream is closed.
     * @return A stream of the lines read from {@code source}.
     */
    static Stream<String> lines(InputStream source, AutoCloseable... alsoClose) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(source)));
        List<AutoCloseable> resources = new ArrayList<>(alsoClose.length + 1);
        resources.add(reader);
        resources.addAll(Arrays.asList(alsoClose));
        return reader.lines().onClose(() -> closeAll(resources));
    }

    /**
     * Close every resource, in iteration order, even when one of them fails to close. The
     * collection is snapshot first, so it may be appended to while the stream is being consumed.
     * @param resources The resources to release. {@code null} entries are ignored.
     * @throws UncheckedIOException if one or more resources failed to close.
     */
    static void closeAll(Collection<? extends AutoCloseable> resources) {
        IOException failure = null;
        for (AutoCloseable resource : new ArrayList<AutoCloseable>(resources)) {
            if (resource == null)
                continue;
            try {
                resource.close();
            } catch (Exception e) {
                IOException cause = asIOException(e);
                if (failure == null)
                    failure = cause;
                else
                    failure.addSuppressed(cause);
            }
        }
        if (failure != null)
            throw new UncheckedIOException(failure);
    }

    /**
     * Release a resource that is being abandoned on a failure path, where the failure that got
     * us here is the one worth reporting.
     * @param resource The resource to release, may be {@code null}.
     */
    static void closeQuietly(AutoCloseable resource) {
        if (resource == null)
            return;
        try {
            resource.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to release resource", e);
        }
    }

    private static IOException asIOException(Exception e) {
        if (e instanceof UncheckedIOException)
            return ((UncheckedIOException) e).getCause();
        if (e instanceof IOException)
            return (IOException) e;
        return new IOException(e);
    }
}
