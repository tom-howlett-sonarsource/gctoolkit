// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.integration;

import com.microsoft.gctoolkit.GCToolKit;
import com.microsoft.gctoolkit.integration.aggregation.PauseTimeSummary;
import com.microsoft.gctoolkit.integration.io.TestLogFile;
import com.microsoft.gctoolkit.io.SingleGCLogFile;
import com.microsoft.gctoolkit.jvm.JavaVirtualMachine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("modulePath")
class ZipStreamLifecycleIntegrationTest {

    @Test
    void analyzingZipClosesFormatDiaryAndAnalysisStreams() throws IOException {
        Path path = new TestLogFile("icms/details/tenuring/par.cms.wt.wd.ast.log.zip").getFile().toPath();
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        SingleGCLogFile log = new SingleGCLogFile(path) {
            @Override
            public Stream<String> stream() throws IOException {
                Stream<String> lines = super.stream();
                opened.incrementAndGet();
                return lines.onClose(closed::incrementAndGet);
            }
        };
        log.diary();
        assertTrue(opened.get() >= 2);
        assertEquals(opened.get(), closed.get());

        GCToolKit toolkit = new GCToolKit();
        toolkit.loadAggregation(new PauseTimeSummary());
        int beforeAnalysis = opened.get();
        JavaVirtualMachine machine = toolkit.analyze(log);
        assertTrue(machine.getAggregation(PauseTimeSummary.class).isPresent());
        assertTrue(opened.get() > beforeAnalysis);
        assertEquals(opened.get(), closed.get());
    }
}
