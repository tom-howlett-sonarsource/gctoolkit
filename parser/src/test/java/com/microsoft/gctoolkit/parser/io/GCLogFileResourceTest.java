// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.SingleGCLogFile;
import com.microsoft.gctoolkit.jvm.Diary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileResourceTest {

    @Test
    void diaryClosesStreamsAfterExhaustionAndUsesCachedResult() throws Exception {
        TrackingLogFile file = new TrackingLogFile(1);
        Diary diary = file.diary();
        assertEquals(2, file.opened);
        assertEquals(file.opened, file.closed);
        assertSame(diary, file.diary());
        assertEquals(2, file.opened);
    }

    @Test
    void diaryClosesStreamsAfterShortCircuit() throws Exception {
        TrackingLogFile file = new TrackingLogFile(20_000);
        assertTrue(file.diary().isG1GC());
        assertEquals(2, file.opened);
        assertEquals(file.opened, file.closed);
        assertTrue(file.consumed < 20_000);
    }

    private static class TrackingLogFile extends SingleGCLogFile {
        private final long lineCount;
        private int opened;
        private int closed;
        private int consumed;

        TrackingLogFile(long lineCount) {
            super(Path.of("unused.zip"));
            this.lineCount = lineCount;
        }

        @Override
        public Stream<String> stream() {
            opened++;
            return Stream.generate(() -> "[0.001s][info][gc] Using G1")
                    .limit(lineCount)
                    .map(line -> {
                        consumed++;
                        return line;
                    })
                    .onClose(() -> closed++);
        }
    }
}
