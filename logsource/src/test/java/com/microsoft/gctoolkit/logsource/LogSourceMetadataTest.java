// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogSourceMetadataTest {

    private static final String FIRST_LINE = "first";
    private static final String SECOND_LINE = "second";
    private static final String ZIP_LOG_ENTRY = "logs/gc.log";

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainTextSources() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log");
        Files.write(log, List.of(FIRST_LINE, SECOND_LINE));

        LogSourceMetadata metadata = new LogSourceMetadata(log);

        assertEquals(LogSourceFormat.PLAINTEXT, metadata.getFormat());
        assertEquals(1, metadata.getNumberOfFiles());
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), LogSourceStreams.stream(metadata).collect(Collectors.toList()));
    }

    @Test
    void discoversGZipSources() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(log))) {
            outputStream.write((FIRST_LINE + "\n" + SECOND_LINE + "\n").getBytes(StandardCharsets.UTF_8));
        }

        LogSourceMetadata metadata = new LogSourceMetadata(log);

        assertEquals(LogSourceFormat.GZIP, metadata.getFormat());
        assertEquals(1, metadata.getNumberOfFiles());
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), LogSourceStreams.stream(metadata).collect(Collectors.toList()));
    }

    @Test
    void discoversZipSourcesAndStreamsFirstFileEntry() throws IOException {
        Path log = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(log))) {
            zipOutputStream.putNextEntry(new ZipEntry("logs/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry(ZIP_LOG_ENTRY));
            zipOutputStream.write((FIRST_LINE + "\n" + SECOND_LINE + "\n").getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }

        LogSourceMetadata metadata = new LogSourceMetadata(log);

        assertEquals(LogSourceFormat.ZIP, metadata.getFormat());
        assertEquals(1, metadata.getNumberOfFiles());
        assertEquals(List.of(ZIP_LOG_ENTRY), LogSourceStreams.zipEntryNames(log));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), LogSourceStreams.stream(metadata).collect(Collectors.toList()));
        try (Stream<String> lines = LogSourceStreams.streamZipEntry(log, ZIP_LOG_ENTRY)) {
            assertEquals(List.of(FIRST_LINE, SECOND_LINE), lines.collect(Collectors.toList()));
        }
        assertEquals(List.of(), LogSourceStreams.streamZipEntry(log, "missing.log").collect(Collectors.toList()));
    }

    @Test
    void streamsEmptyZipSourcesAsEmpty() throws IOException {
        Path log = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(log))) {
            zipOutputStream.putNextEntry(new ZipEntry("logs/"));
            zipOutputStream.closeEntry();
        }

        LogSourceMetadata metadata = new LogSourceMetadata(log);

        assertEquals(LogSourceFormat.ZIP, metadata.getFormat());
        assertEquals(0, metadata.getNumberOfFiles());
        assertEquals(List.of(), LogSourceStreams.stream(metadata).collect(Collectors.toList()));
    }

    @Test
    void discoversDirectorySources() throws IOException {
        Files.write(temporaryDirectory.resolve("gc.log.0"), List.of(FIRST_LINE));
        Files.write(temporaryDirectory.resolve("gc.log.1"), List.of(SECOND_LINE));

        LogSourceMetadata metadata = new LogSourceMetadata(temporaryDirectory);

        assertEquals(LogSourceFormat.DIRECTORY, metadata.getFormat());
        assertEquals(2, metadata.getNumberOfFiles());
        assertEquals(List.of(), LogSourceStreams.stream(metadata).collect(Collectors.toList()));
    }

    @Test
    void keepsOnlyTheRequestedTailLines() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log");
        Files.write(log, Arrays.asList("1", "2", "3", "4", "5"));

        assertEquals(List.of("3", "4", "5"), LogSourceStreams.tail(log, 3));
        try (var lines = Files.lines(log)) {
            assertEquals(List.of("4", "5"), lines.collect(LogSourceStreams.tail(2)));
        }
        assertEquals(List.of("3", "4", "5"), Stream.of("1", "2", "3", "4", "5").parallel().collect(LogSourceStreams.tail(3)));
        assertEquals(List.of(), LogSourceStreams.tail(log, 0));
        assertThrows(IllegalArgumentException.class, () -> LogSourceStreams.tail(-1));
    }

    @Test
    void reportsUnknownSources() throws IOException {
        LogSourceMetadata metadata = new LogSourceMetadata(temporaryDirectory.resolve("missing.log"));

        assertEquals(LogSourceFormat.UNKNOWN, metadata.getFormat());
        assertEquals(0, metadata.getNumberOfFiles());
        assertEquals(List.of(), LogSourceStreams.stream(metadata).collect(Collectors.toList()));
    }
}
