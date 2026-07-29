// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/**
 * Creates streams of lines that release the resources they read from.
 * <p>
 * {@link BufferedReader#lines()} leaves its reader, and therefore the archive that reader draws
 * from, open. The streams handed out here register a close handler so that closing the stream,
 * whether or not it has been fully consumed, releases every underlying resource.
 */
final class LineStreams {

    private LineStreams() {
    }

    /**
     * Stream the lines read from {@code source}.
     * @param source The bytes to read lines from. Closed when the returned stream is closed.
     * @param archives Resources that hold {@code source} open. Closed, in the order given,
     *                 after {@code source} when the returned stream is closed.
     * @return A stream of lines that releases {@code source} and {@code archives} when closed.
     */
    static Stream<String> lines(InputStream source, Closeable... archives) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(source)));
        return reader.lines().onClose(() -> close(reader, archives));
    }

    /**
     * Close {@code resource} while {@code failure} is on its way to the caller, recording an
     * unsuccessful close as a suppressed exception so that the original failure is not lost.
     * @param resource The resource to close.
     * @param failure The exception being propagated.
     */
    static void closeSuppressing(Closeable resource, Exception failure) {
        try {
            resource.close();
        } catch (IOException ioe) {
            failure.addSuppressed(ioe);
        }
    }

    /**
     * Close every resource, even if one of them fails to close. The first failure is reported,
     * the rest are suppressed.
     */
    private static void close(Closeable reader, Closeable... archives) {
        IOException failure = null;
        try {
            reader.close();
        } catch (IOException ioe) {
            failure = ioe;
        }
        for (Closeable archive : archives) {
            try {
                archive.close();
            } catch (IOException ioe) {
                if (failure == null)
                    failure = ioe;
                else
                    failure.addSuppressed(ioe);
            }
        }
        if (failure != null)
            throw new UncheckedIOException(failure);
    }
}
