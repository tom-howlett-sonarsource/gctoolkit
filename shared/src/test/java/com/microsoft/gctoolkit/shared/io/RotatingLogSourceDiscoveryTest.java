// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogSourceDiscoveryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void discoversSegmentsMatchingTheCurrentLogRoot() throws IOException {
        Path older = tempDirectory.resolve("gc.log.0");
        Path current = tempDirectory.resolve("gc.log");
        Files.write(older, List.of("10", "20"));
        Files.write(current, List.of("30", "40"));
        Files.write(tempDirectory.resolve("other.log"), List.of("50", "60"));

        List<TestSegment> segments = RotatingLogSourceDiscovery.discover(
                current,
                LogSourceFormat.PLAINTEXT,
                TestSegment::new,
                (path, name) -> new TestSegment(path));

        assertEquals(
                List.of("gc.log.0", "gc.log"),
                segments.stream().map(TestSegment::getSegmentName).collect(Collectors.toList()));
    }

    private static class TestSegment implements LogSourceSegment {
        private final Path path;

        TestSegment(Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getSegmentName() {
            return path.getFileName().toString();
        }

        @Override
        public double getStartTime() {
            return readLine(0);
        }

        @Override
        public double getEndTime() {
            return readLine(1);
        }

        @Override
        public Stream<String> stream() {
            try {
                return Files.lines(path);
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }

        private double readLine(int index) {
            try {
                return Double.parseDouble(Files.readAllLines(path).get(index));
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }
    }
}
