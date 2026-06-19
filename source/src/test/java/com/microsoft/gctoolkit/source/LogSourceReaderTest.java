// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.stream.Collectors.toList;

public class LogSourceReaderTest {

    private static final String FIRST_LINE = "first";
    private static final String SECOND_LINE = "second";
    private static final String LOG_FILE_NAME = "gc.log";
    private static final String ZIP_FILE_NAME = "gc.zip";

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainTextFilesWithoutBlankLines() throws IOException {
        Path logFile = temporaryDirectory.resolve(LOG_FILE_NAME);
        Files.writeString(logFile, " " + FIRST_LINE + " \n\n" + SECOND_LINE + "\n");

        try (Stream<String> lines = LogSourceReader.streamNonBlank(new LogSourceMetadata(logFile))) {
            assertEquals(List.of(FIRST_LINE, SECOND_LINE), lines.collect(toList()));
        }
    }

    @Test
    void streamsFirstFileFromZip() throws IOException {
        Path zipFile = temporaryDirectory.resolve(ZIP_FILE_NAME);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zipOutputStream.putNextEntry(new ZipEntry(LOG_FILE_NAME));
            zipOutputStream.write((FIRST_LINE + "\n" + SECOND_LINE + "\n").getBytes());
            zipOutputStream.closeEntry();
        }

        LogSourceMetadata metadata = new LogSourceMetadata(zipFile);

        assertTrue(metadata.isZip());
        try (Stream<String> lines = LogSourceReader.stream(metadata)) {
            assertEquals(List.of(FIRST_LINE, SECOND_LINE), lines.collect(toList()));
        }
    }

    @Test
    void streamsGZipFiles() throws IOException {
        Path gzipFile = temporaryDirectory.resolve(LOG_FILE_NAME + ".gz");
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(gzipFile))) {
            gzipOutputStream.write((FIRST_LINE + "\n" + SECOND_LINE + "\n").getBytes());
        }

        LogSourceMetadata metadata = new LogSourceMetadata(gzipFile);

        assertTrue(metadata.isGZip());
        try (Stream<String> lines = LogSourceReader.stream(metadata)) {
            assertEquals(List.of(FIRST_LINE, SECOND_LINE), lines.collect(toList()));
        }
    }

    @Test
    void streamsNamedZipEntry() throws IOException {
        Path zipFile = temporaryDirectory.resolve(ZIP_FILE_NAME);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zipOutputStream.putNextEntry(new ZipEntry("first.log"));
            zipOutputStream.write(FIRST_LINE.getBytes());
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("second.log"));
            zipOutputStream.write(SECOND_LINE.getBytes());
            zipOutputStream.closeEntry();
        }

        try (Stream<String> lines = LogSourceReader.streamZipEntry(zipFile, "second.log")) {
            assertEquals(List.of(SECOND_LINE), lines.collect(toList()));
        }
    }

    @Test
    void failsWhenNamedZipEntryIsMissing() throws IOException {
        Path zipFile = temporaryDirectory.resolve(ZIP_FILE_NAME);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zipOutputStream.putNextEntry(new ZipEntry(LOG_FILE_NAME));
            zipOutputStream.write(FIRST_LINE.getBytes());
            zipOutputStream.closeEntry();
        }

        assertThrows(IOException.class, () -> LogSourceReader.streamZipEntry(zipFile, "missing.log"));
    }

    @Test
    void failsWhenSourceTypeCannotBeStreamed() throws IOException {
        LogSourceMetadata metadata = new LogSourceMetadata(temporaryDirectory);

        assertThrows(IOException.class, () -> LogSourceReader.stream(metadata));
    }
}
