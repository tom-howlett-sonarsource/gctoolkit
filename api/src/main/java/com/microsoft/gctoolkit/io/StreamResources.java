// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/**
 * Ties the resources backing a stream of log lines to {@link Stream#close()}.
 * <p>
 * {@link BufferedReader#lines()} leaves the reader, and everything the reader draws from,
 * open when the returned stream is closed. Log lines read out of an archive are backed by
 * resources that must be released, so the line streams handed out by this package are
 * wrapped here rather than returned bare.
 */
final class StreamResources {

    private StreamResources() {
    }

    /**
     * Stream the lines read by {@code reader}. Closing the returned stream closes
     * {@code reader} and then each of {@code resources}, whether or not the stream
     * was fully consumed.
     * @param reader The reader supplying the lines.
     * @param resources Resources that the reader does not itself close, such as the
     *                  archive the reader draws an entry from.
     * @return A stream of lines that owns {@code reader} and {@code resources}.
     */
    static Stream<String> lines(BufferedReader reader, Closeable... resources) {
        Closeable[] owned = new Closeable[resources.length + 1];
        owned[0] = reader;
        System.arraycopy(resources, 0, owned, 1, resources.length);
        return reader.lines().onClose(() -> closeAll(owned));
    }

    /**
     * Close every resource, in order, even when one of them fails. The first failure is
     * thrown once all resources have been visited, with later failures suppressed. An
     * {@link IOException} is reported as an {@link UncheckedIOException}, as
     * {@link java.nio.file.Files#lines(java.nio.file.Path)} does.
     * @param resources The resources to close.
     */
    static void closeAll(Closeable... resources) {
        RuntimeException failure = null;
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException ioe) {
                failure = accumulate(failure, new UncheckedIOException(ioe));
            } catch (RuntimeException re) {
                failure = accumulate(failure, re);
            }
        }
        if (failure != null)
            throw failure;
    }

    /**
     * Close {@code resource} while {@code failure} is on its way to the caller, recording
     * an unsuccessful close as suppressed so that the original failure is not lost. This is
     * the clean up a try-with-resources block performs, for a resource that is handed to
     * the caller when no failure occurs.
     * @param resource The resource to close.
     * @param failure The failure being propagated.
     */
    static void closeSuppressing(Closeable resource, Throwable failure) {
        try {
            resource.close();
        } catch (IOException | RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    private static RuntimeException accumulate(RuntimeException failure, RuntimeException error) {
        if (failure == null)
            return error;
        failure.addSuppressed(error);
        return failure;
    }
}
