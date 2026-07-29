// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    private static final byte[] ROTATED = "rotated segment\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ACTIVE = "active segment\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void sumsEverySegmentInADirectory() throws IOException {
        Files.write(directory.resolve("gc.log.0"), ROTATED);
        Files.write(directory.resolve("gc.log"), ACTIVE);

        assertEquals(ROTATED.length + ACTIVE.length, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void ignoresNestedDirectoriesWhenSummingADirectory() throws IOException {
        Files.write(directory.resolve("gc.log"), ACTIVE);
        Files.createDirectory(directory.resolve("archive"));

        assertEquals(ACTIVE.length, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void returnsZeroForAnEmptyDirectory() throws IOException {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void sumsSiblingsWhenConstructedFromAMemberOfTheRotatingSet() throws IOException {
        Files.write(directory.resolve("gc.log.0"), ROTATED);
        Path active = Files.write(directory.resolve("gc.log"), ACTIVE);
        Files.write(directory.resolve("unrelated.log"), ACTIVE);

        long expected = ROTATED.length + ACTIVE.length;
        assertEquals(expected, new RotatingLogFileMetadata(active).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(directory.resolve("gc.log.0")).getTotalByteSize());
    }

    @Test
    void sumsUncompressedSizesOfZipEntriesSkippingDirectories() throws IOException {
        Path archive = zip(directory.resolve("gc.zip"), true);

        assertEquals(ROTATED.length + ACTIVE.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void returnsZeroForAZipHoldingOnlyDirectories() throws IOException {
        Path archive = zip(directory.resolve("empty.zip"), false);

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void returnsZeroForAnUnsupportedFormat() throws IOException {
        Path gzip = Files.write(directory.resolve("gc.log.gz"),
                new byte[] {(byte) LogFileMetadata.GZIP_MAGIC1, (byte) LogFileMetadata.GZIP_MAGIC2, 0, 0});

        assertEquals(0L, new RotatingLogFileMetadata(gzip).getTotalByteSize());
    }

    @Test
    void preservesDiscoveryAndOrderingOfRotatingSegments() throws IOException {
        Path active = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(active);

        long expected = Files.size(active) + Files.size(active.resolveSibling("G1-80-16gbps2.log.0"));
        assertEquals(expected, metadata.getTotalByteSize());

        assertEquals(2, metadata.getNumberOfFiles());
        List<String> names = metadata.logFiles().map(LogFileSegment::getSegmentName).collect(Collectors.toList());
        assertEquals(List.of("G1-80-16gbps2.log.0", "G1-80-16gbps2.log"), names);
        assertEquals(expected, metadata.getTotalByteSize());
    }

    private Path zip(Path archive, boolean withFiles) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            addEntry(output, "logs/", new byte[0]);
            if (withFiles) {
                addEntry(output, "logs/gc.log.0", ROTATED);
                addEntry(output, "logs/gc.log", ACTIVE);
            }
        }
        return archive;
    }

    private void addEntry(ZipOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}
