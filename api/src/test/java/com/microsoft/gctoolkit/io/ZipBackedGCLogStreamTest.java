package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path zip = createZip("single.zip", Map.of("gc.log", " first line \nsecond line\n"));
        long descriptorsBefore = openDescriptorsFor(zip);

        Stream<String> lines = new SingleGCLogFile(zip).stream();
        Iterator<String> iterator = lines.iterator();
        assertEquals("first line", iterator.next());
        assertTrue(openDescriptorsFor(zip) > descriptorsBefore);

        lines.close();

        assertEquals(descriptorsBefore, openDescriptorsFor(zip));
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path zip = createZip("segment.zip", Map.of("gc.log.1", "[1.000s] first\n[1.100s] second\n"));
        long descriptorsBefore = openDescriptorsFor(zip);

        Stream<String> lines = new GCLogFileZipSegment(zip, "gc.log.1").stream();
        Iterator<String> iterator = lines.iterator();
        assertEquals("[1.000s] first", iterator.next());
        assertTrue(openDescriptorsFor(zip) > descriptorsBefore);

        lines.close();

        assertEquals(descriptorsBefore, openDescriptorsFor(zip));
    }

    @Test
    void rotatingGCLogFileClosesCurrentZipSegmentAfterPartialConsumption() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "1.000: old first\n1.100: old second\n");
        entries.put("gc.log.1.current", "2.000: current first\n2.100: current second\n");
        Path zip = createZip("rotating.zip", entries);
        long descriptorsBefore = openDescriptorsFor(zip);

        Stream<String> lines = new RotatingGCLogFile(zip).stream();
        Iterator<String> iterator = lines.iterator();
        assertEquals("1.000: old first", iterator.next());

        lines.close();

        assertEquals(descriptorsBefore, openDescriptorsFor(zip));
    }

    @Test
    void rotatingZipPreservesContentOrderAndSentinel() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "1.000: old first \n\n1.100: old second\n");
        entries.put("gc.log.1.current", "2.000: current first\n2.100: current second \n");
        Path zip = createZip("ordered.zip", entries);
        RotatingGCLogFile logFile = new RotatingGCLogFile(zip);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of(
                    "1.000: old first",
                    "1.100: old second",
                    "2.000: current first",
                    "2.100: current second",
                    logFile.endOfData()), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void closeableLineStreamReportsReaderCloseFailure() {
        AtomicInteger closeAttempts = new AtomicInteger();
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                closeAttempts.incrementAndGet();
                throw new IOException("close failed");
            }
        };
        Closeable archive = closeAttempts::incrementAndGet;

        Stream<String> lines = CloseableStreams.lines(reader, archive);

        assertThrows(UncheckedIOException.class, lines::close);
        assertEquals(2, closeAttempts.get());
    }

    @Test
    void rotatingStreamCloseAttemptsEveryOpenedSegment() {
        AtomicInteger closeAttempts = new AtomicInteger();
        Stream<String> first = Stream.<String>empty().onClose(() -> {
            closeAttempts.incrementAndGet();
            throw new IllegalStateException("first");
        });
        Stream<String> second = Stream.<String>empty().onClose(() -> {
            closeAttempts.incrementAndGet();
            throw new IllegalArgumentException("second");
        });

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> RotatingGCLogFile.closeStreams(List.of(first, second)));

        assertEquals(2, closeAttempts.get());
        assertEquals(1, exception.getSuppressed().length);
    }

    private Path createZip(String fileName, Map<String, String> entries) throws IOException {
        Path zip = temporaryDirectory.resolve(fileName);
        try (OutputStream output = Files.newOutputStream(zip);
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutput.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutput.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutput.closeEntry();
            }
        }
        return zip;
    }

    private long openDescriptorsFor(Path path) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptors), "File descriptor assertions require procfs");
        Path target = path.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> openDescriptors = Files.newDirectoryStream(descriptors)) {
            for (Path descriptor : openDescriptors) {
                if (isDescriptorFor(descriptor, target)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isDescriptorFor(Path descriptor, Path target) {
        try {
            return Files.readSymbolicLink(descriptor).equals(target);
        } catch (IOException ignored) {
            return false;
        }
    }
}
