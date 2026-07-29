// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileStreamsTest {

    @Test
    void linesReadsEveryLineAndReleasesTheSourceAndTheArchiveOnClose() {
        RecordingInputStream source = new RecordingInputStream("first\nsecond\n");
        RecordingResource archive = new RecordingResource();

        List<String> lines;
        try (Stream<String> stream = LogFileStreams.lines(source, archive)) {
            lines = stream.collect(Collectors.toList());
            assertEquals(0, archive.closes(), "the archive has to stay open while the lines are read");
        }

        assertEquals(Arrays.asList("first", "second"), lines);
        assertEquals(1, source.closes());
        assertEquals(1, archive.closes());
    }

    @Test
    void linesReleasesResourcesWhenOnlyPartiallyConsumed() {
        RecordingInputStream source = new RecordingInputStream("first\nsecond\nthird\n");
        RecordingResource archive = new RecordingResource();

        try (Stream<String> stream = LogFileStreams.lines(source, archive)) {
            assertEquals("first", stream.findFirst().orElseThrow(AssertionError::new));
        }

        assertEquals(1, source.closes());
        assertEquals(1, archive.closes());
    }

    @Test
    void closingTheStreamTwiceReleasesTheResourcesOnlyOnce() {
        RecordingInputStream source = new RecordingInputStream("first\n");
        RecordingResource archive = new RecordingResource();

        Stream<String> stream = LogFileStreams.lines(source, archive);
        stream.close();
        stream.close();

        assertEquals(1, source.closes());
        assertEquals(1, archive.closes());
    }

    @Test
    void closeAllClosesEveryResourceEvenWhenOneOfThemFails() {
        IOException expected = new IOException("cannot close");
        RecordingResource first = new RecordingResource();
        RecordingResource second = new RecordingResource(expected);
        RecordingResource third = new RecordingResource();
        List<AutoCloseable> resources = Arrays.asList(first, second, third);

        UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> LogFileStreams.closeAll(resources));

        assertSame(expected, failure.getCause());
        assertEquals(1, first.closes());
        assertEquals(1, second.closes());
        assertEquals(1, third.closes(), "a failure must not stop the remaining resources being released");
    }

    @Test
    void closeAllReportsEveryFailureItCollected() {
        IOException first = new IOException("first failure");
        RuntimeException second = new IllegalStateException("second failure");
        List<AutoCloseable> resources = Arrays.asList(new RecordingResource(first), new RecordingResource(second));

        UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> LogFileStreams.closeAll(resources));

        assertSame(first, failure.getCause());
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertSame(second, failure.getCause().getSuppressed()[0].getCause());
    }

    @Test
    void closeAllUnwrapsAnUncheckedIOExceptionRaisedByAStreamItCloses() {
        IOException expected = new IOException("stream close failed");
        Stream<String> stream = Stream.<String>empty().onClose(() -> {
            throw new UncheckedIOException(expected);
        });

        UncheckedIOException failure = assertThrows(UncheckedIOException.class,
                () -> LogFileStreams.closeAll(Collections.singletonList(stream)));

        assertSame(expected, failure.getCause());
    }

    @Test
    void closeAllSkipsNullResourcesAndToleratesConcurrentAdditions() {
        RecordingResource resource = new RecordingResource();
        List<AutoCloseable> resources = Collections.synchronizedList(new ArrayList<>(Arrays.asList(null, resource)));

        LogFileStreams.closeAll(resources);

        assertEquals(1, resource.closes());
    }

    @Test
    void closeQuietlyToleratesNullAndSwallowsFailures() {
        RecordingResource failing = new RecordingResource(new IOException("cannot close"));

        LogFileStreams.closeQuietly(null);
        LogFileStreams.closeQuietly(failing);

        assertEquals(1, failing.closes());
    }

    @Test
    void linesAcceptsASourceWithoutAdditionalResources() {
        RecordingInputStream source = new RecordingInputStream("only\n");

        try (Stream<String> stream = LogFileStreams.lines(source)) {
            assertTrue(stream.anyMatch("only"::equals));
        }

        assertEquals(1, source.closes());
    }

    private static class RecordingInputStream extends ByteArrayInputStream {

        private int closes;

        RecordingInputStream(String contents) {
            super(contents.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closes++;
            super.close();
        }

        int closes() {
            return closes;
        }
    }

    private static class RecordingResource implements AutoCloseable {

        private final Exception failure;
        private int closes;

        RecordingResource() {
            this(null);
        }

        RecordingResource(Exception failure) {
            this.failure = failure;
        }

        @Override
        public void close() throws Exception {
            closes++;
            if (failure != null)
                throw failure;
        }

        int closes() {
            return closes;
        }
    }
}
