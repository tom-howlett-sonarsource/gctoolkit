// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;

final class StreamResources {

    private StreamResources() {
    }

    static Runnable close(Closeable resource) {
        return () -> {
            try {
                resource.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
    }
}
