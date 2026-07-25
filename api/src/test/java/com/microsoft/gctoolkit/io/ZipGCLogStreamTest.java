package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", Map.of(
                "gc.log", " first line \n\n second line \n"));
        long descriptorsBefore = openDescriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            if (descriptorsBefore >= 0) {
                assertTrue(openDescriptorsFor(archive) > descriptorsBefore);
            }
        }

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
        assertArchiveCanBeMoved(archive);
    }

    @Test
    void singleGCLogFilePreservesContentsAndSentinel() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("logs/", "");
        entries.put("logs/gc.log", " first line \n\n second line \n");
        Path archive = createZip("single-with-directory.zip", entries);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(toList());
        }

        assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void singleGCLogFileClosesGZipAfterPartialConsumption() throws IOException {
        Path archive = temporaryDirectory.resolve("single.gz");
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(archive))) {
            gzip.write("first line\nsecond line\n".getBytes(StandardCharsets.UTF_8));
        }
        long descriptorsBefore = openDescriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            if (descriptorsBefore >= 0) {
                assertTrue(openDescriptorsFor(archive) > descriptorsBefore);
            }
        }

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
        assertArchiveCanBeMoved(archive);
    }

    @Test
    void singleGCLogFileClosesZipWhenEntryInitializationFails() throws IOException {
        Path archive = createUnsupportedEncryptedZip();
        long descriptorsBefore = openDescriptorsFor(archive);

        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
        assertArchiveCanBeMoved(archive);
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", Map.of(
                "gc.log.0", "first line\nsecond line\n"));
        long descriptorsBefore = openDescriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            if (descriptorsBefore >= 0) {
                assertTrue(openDescriptorsFor(archive) > descriptorsBefore);
            }
        }

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
        assertArchiveCanBeMoved(archive);
    }

    @Test
    void rotatingGCLogFileClosesEveryArchiveResourceAfterPartialConsumption() throws IOException {
        Path archive = createRotatingZip();
        long descriptorsBefore = openDescriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.100s][info][gc] older first", lines.iterator().next());
        }

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
        assertArchiveCanBeMoved(archive);
    }

    @Test
    void rotatingGCLogFilePreservesContentsOrderAndSentinel() throws IOException {
        Path archive = createRotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(toList());
        }

        assertEquals(List.of(
                "[0.100s][info][gc] older first",
                "[0.200s][info][gc] older second",
                "[1.000s][info][gc] current first",
                "[1.100s][info][gc] current second",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void closeHandlerWrapsIOException() {
        Runnable closeHandler = StreamResources.close(() -> {
            throw new IOException("close failed");
        });

        assertThrows(UncheckedIOException.class, closeHandler::run);
    }

    private Path createRotatingZip() throws IOException {
        return createZip("rotating.zip", Map.of(
                "gc.log.0", "[0.100s][info][gc] older first \n[0.200s][info][gc] older second\n",
                "gc.log", "[1.000s][info][gc] current first\n\n[1.100s][info][gc] current second \n"));
    }

    private Path createZip(String fileName, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private Path createUnsupportedEncryptedZip() throws IOException {
        ByteBuffer localFileHeader = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN);
        localFileHeader.putInt(0x04034b50);
        localFileHeader.putShort((short) 20);
        localFileHeader.putShort((short) 1);
        localFileHeader.putShort((short) 0);
        localFileHeader.putShort((short) 0);
        localFileHeader.putShort((short) 0);
        localFileHeader.putInt(0);
        localFileHeader.putInt(0);
        localFileHeader.putInt(0);
        localFileHeader.putShort((short) 1);
        localFileHeader.putShort((short) 0);
        localFileHeader.put((byte) 'x');
        Path archive = temporaryDirectory.resolve("unsupported-encrypted.zip");
        Files.write(archive, localFileHeader.array());
        return archive;
    }

    private long openDescriptorsFor(Path archive) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        if (!Files.isDirectory(descriptors)) {
            return -1L;
        }
        String expectedTarget = archive.toAbsolutePath().toString();
        try (Stream<Path> paths = Files.list(descriptors)) {
            return paths.filter(path -> expectedTarget.equals(readSymbol(path))).count();
        }
    }

    private void assertArchiveCanBeMoved(Path archive) throws IOException {
        Path movedArchive = archive.resolveSibling(archive.getFileName() + ".moved");
        Files.move(archive, movedArchive);
        Files.move(movedArchive, archive);
    }

    private String readSymbol(Path path) {
        try {
            return Files.readSymbolicLink(path).toString();
        } catch (IOException ignored) {
            return null;
        }
    }
}
