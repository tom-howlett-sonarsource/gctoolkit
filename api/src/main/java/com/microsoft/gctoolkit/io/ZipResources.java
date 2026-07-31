// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Closes the archive resources backing a ZIP-based GC log stream. Used as a
 * {@link java.util.stream.Stream#onClose(Runnable)} action so every resource is released
 * once the caller closes the returned stream, however much of it was consumed.
 */
final class ZipResources {

    private ZipResources() {
    }

    static void closeQuietly(Closeable... closeables) {
        UncheckedIOException failure = null;
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = new UncheckedIOException(e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
