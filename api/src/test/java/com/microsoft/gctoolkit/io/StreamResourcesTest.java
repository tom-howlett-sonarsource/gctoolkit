// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamResourcesTest {

    private final List<String> closed = new ArrayList<>();

    @Test
    void linesAreReadFromTheReader() {
        try (Stream<String> lines = StreamResources.lines(reader("first\nsecond"))) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void closingTheLineStreamClosesTheReaderThenTheResourcesItDrawsFrom() {
        Stream<String> lines = StreamResources.lines(reader("first\nsecond"), resource("archive"));
        lines.forEach(line -> { });
        assertEquals(List.of(), closed, "the resources belong to the stream, not to the traversal");

        lines.close();

        assertEquals(List.of("reader", "archive"), closed);
    }

    @Test
    void closingAPartiallyConsumedLineStreamClosesEveryResource() {
        try (Stream<String> lines = StreamResources.lines(reader("first\nsecond"), resource("archive"))) {
            assertEquals("first", lines.findFirst().orElseThrow());
        }

        assertEquals(List.of("reader", "archive"), closed);
    }

    @Test
    void closingTheLineStreamTwiceClosesTheResourcesOnce() {
        Stream<String> lines = StreamResources.lines(reader("first"), resource("archive"));

        lines.close();
        lines.close();

        assertEquals(List.of("reader", "archive"), closed);
    }

    @Test
    void everyResourceIsClosedInOrder() {
        StreamResources.closeAll(resource("first"), resource("second"), resource("third"));

        assertEquals(List.of("first", "second", "third"), closed);
    }

    @Test
    void everyResourceIsClosedWhenOneOfThemFails() {
        IOException failure = new IOException("archive is gone");
        Closeable[] resources = {resource("first"), failing(failure), resource("third")};

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, () -> StreamResources.closeAll(resources));

        assertEquals(List.of("first", "third"), closed);
        assertSame(failure, thrown.getCause());
    }

    @Test
    void laterFailuresToCloseAreSuppressed() {
        IOException first = new IOException("first failure");
        IOException second = new IOException("second failure");
        Closeable[] resources = {failing(first), failing(second)};

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, () -> StreamResources.closeAll(resources));

        assertSame(first, thrown.getCause());
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(second, ((UncheckedIOException) thrown.getSuppressed()[0]).getCause());
    }

    @Test
    void anUncheckedFailureToCloseIsThrownAsItIs() {
        IllegalStateException failure = new IllegalStateException("zip file closed");
        Closeable[] resources = {() -> { throw failure; }, resource("second")};

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> StreamResources.closeAll(resources));

        assertSame(failure, thrown);
        assertEquals(List.of("second"), closed);
    }

    @Test
    void closeSuppressingRecordsAFailureToCloseAgainstThePropagatingFailure() {
        IOException failureToClose = new IOException("archive is gone");
        IOException propagating = new IOException("entry is missing");

        StreamResources.closeSuppressing(failing(failureToClose), propagating);

        assertEquals(1, propagating.getSuppressed().length);
        assertSame(failureToClose, propagating.getSuppressed()[0]);
    }

    @Test
    void closeSuppressingLeavesThePropagatingFailureAloneWhenTheResourceCloses() {
        IOException propagating = new IOException("entry is missing");

        StreamResources.closeSuppressing(resource("archive"), propagating);

        assertEquals(List.of("archive"), closed);
        assertEquals(0, propagating.getSuppressed().length);
    }

    private BufferedReader reader(String contents) {
        return new BufferedReader(new StringReader(contents)) {
            @Override
            public void close() throws IOException {
                closed.add("reader");
                super.close();
            }
        };
    }

    private Closeable resource(String name) {
        return () -> closed.add(name);
    }

    private static Closeable failing(IOException failure) {
        return () -> {
            throw failure;
        };
    }
}
