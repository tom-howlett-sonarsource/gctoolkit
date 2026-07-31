// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Closes the archive resources (e.g. {@code ZipFile}, {@code Reader}) backing a
 * {@code Stream<String>} when the stream's {@code close()} is invoked, even if
 * the stream was only partially consumed.
 */
final class CloseableResources {

    private CloseableResources() {
    }

    static void closeAll(Closeable... closeables) {
        IOException failure = null;
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
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
