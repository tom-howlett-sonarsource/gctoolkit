// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosingLinesTest {

    @Test
    void closingStreamClosesInputAndOwner() {
        AtomicBoolean inputClosed = new AtomicBoolean();
        AtomicBoolean ownerClosed = new AtomicBoolean();
        ByteArrayInputStream input = new ByteArrayInputStream("a\nb\n".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                inputClosed.set(true);
            }
        };

        try (Stream<String> lines = ClosingLines.of(input, () -> ownerClosed.set(true))) {
            assertEquals("a", lines.findFirst().orElseThrow());
        }

        assertTrue(inputClosed.get());
        assertTrue(ownerClosed.get());
    }

    @Test
    void ownerCloseFailureIsReportedAsUncheckedIOException() {
        IOException failure = new IOException("boom");
        Stream<String> lines = ClosingLines.of(new ByteArrayInputStream(new byte[0]), () -> { throw failure; });

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, lines::close);
        assertSame(failure, thrown.getCause());
    }

    @Test
    void closeOnFailureClosesResourceAndSuppressesSecondaryFailure() {
        AtomicBoolean closed = new AtomicBoolean();
        IOException primary = new IOException("primary");
        assertSame(primary, ClosingLines.closeOnFailure(() -> closed.set(true), primary));
        assertTrue(closed.get());

        IOException secondary = new IOException("secondary");
        Closeable failing = () -> { throw secondary; };
        assertSame(primary, ClosingLines.closeOnFailure(failing, primary));
        assertArrayEquals(new Throwable[] {secondary}, primary.getSuppressed());
    }
}
