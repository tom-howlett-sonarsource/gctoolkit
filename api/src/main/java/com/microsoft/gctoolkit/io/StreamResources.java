// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Helpers for tying the lifecycle of archive resources to the lifecycle of the
 * {@link Stream} that reads from them.
 */
final class StreamResources {

    private static final Logger LOGGER = Logger.getLogger(StreamResources.class.getName());

    private StreamResources() {
    }

    /**
     * Close a resource, logging rather than propagating any failure. Closing an already
     * closed resource is a no-op, which makes this safe to call from a stream close handler
     * that may run more than once.
     * @param resource the resource to close, may be {@code null}.
     */
    static void closeQuietly(AutoCloseable resource) {
        if (resource == null)
            return;
        try {
            resource.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to release log file resource", e);
        }
    }

    /**
     * Close every resource in the list, even if one of them fails to close.
     * @param resources the resources to close.
     */
    static void closeQuietly(List<? extends AutoCloseable> resources) {
        for (AutoCloseable resource : resources)
            closeQuietly(resource);
    }
}
