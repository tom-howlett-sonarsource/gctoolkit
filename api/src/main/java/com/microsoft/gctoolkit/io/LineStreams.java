// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Creates streams of log lines that release the resources they were built on.
 * <p>
 * {@link BufferedReader#lines()} does not register a close handler, so closing the stream it returns,
 * or abandoning that stream part way through, leaves the reader and any archive it reads from open.
 * The streams created here release those resources when the stream is closed.
 */
final class LineStreams {

    private static final Logger LOGGER = Logger.getLogger(LineStreams.class.getName());

    private LineStreams() {}

    /**
     * Stream the lines read by {@code reader}. Closing the returned stream closes {@code reader}
     * followed by each of {@code alsoClose}, which is where the enclosing archive is passed when
     * closing the reader alone would not release it.
     * @param reader The source of the lines.
     * @param alsoClose Resources to release along with {@code reader}.
     * @return A stream of lines which releases {@code reader} and {@code alsoClose} when closed.
     */
    static Stream<String> lines(BufferedReader reader, AutoCloseable... alsoClose) {
        return reader.lines().onClose(() -> {
            close(reader);
            closeAll(Arrays.asList(alsoClose));
        });
    }

    /**
     * Close every resource, even when one of them fails to close.
     * @param resources The resources to release.
     */
    static void closeAll(Iterable<? extends AutoCloseable> resources) {
        for (AutoCloseable resource : resources)
            close(resource);
    }

    /**
     * Close {@code resource}, ignoring a {@code null}. A log file that cannot be closed is logged
     * rather than reported to the caller; the lines have already been delivered, so failing the
     * close would turn a successful analysis into an error.
     * @param resource The resource to release, may be {@code null}.
     */
    static void close(AutoCloseable resource) {
        if (resource == null)
            return;
        try {
            resource.close();
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Unable to release " + resource.getClass().getName(), exception);
        }
    }
}
