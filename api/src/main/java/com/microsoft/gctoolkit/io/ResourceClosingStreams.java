// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class ResourceClosingStreams {

    private ResourceClosingStreams() { }

    static Stream<String> lines(
            final BufferedReader reader,
            final AutoCloseable... resources) {
        return reader.lines().onClose(() -> closeAll(reader, resources));
    }

    static void closeOnFailure(
            final Throwable failure,
            final AutoCloseable... resources) {
        try {
            closeAll(resources);
        } catch (RuntimeException | Error closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void closeAll(
            final BufferedReader reader,
            final AutoCloseable... resources) {
        Throwable failure = closeResource(reader, null);
        for (AutoCloseable resource : resources) {
            failure = closeResource(resource, failure);
        }
        rethrow(failure);
    }

    private static void closeAll(final AutoCloseable... resources) {
        Throwable failure = null;
        for (AutoCloseable resource : resources) {
            failure = closeResource(resource, failure);
        }
        rethrow(failure);
    }

    private static Throwable closeResource(
            final AutoCloseable resource,
            final Throwable failure) {
        if (resource == null) {
            return failure;
        }
        try {
            resource.close();
        } catch (Throwable closeFailure) {
            if (failure == null) {
                return closeFailure;
            }
            failure.addSuppressed(closeFailure);
        }
        return failure;
    }

    private static void rethrow(final Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof IOException) {
            throw new UncheckedIOException((IOException) failure);
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }
}
