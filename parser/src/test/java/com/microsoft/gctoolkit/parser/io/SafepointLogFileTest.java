// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafepointLogFileTest {

    @TempDir
    Path directory;

    @Test
    void zipStreamReadsOnlyFirstFileAfterDirectories() throws IOException {
        Path archive = writeZip(true);
        try (Stream<String> lines = new SafepointLogFile(archive).streamZipFile()) {
            assertEquals("first\nsecond", lines.collect(Collectors.joining("\n")));
        }
    }

    @Test
    void closingZipStreamClosesReaderBeforeConsumption() throws IOException {
        Stream<String> lines = new SafepointLogFile(writeZip(true)).streamZipFile();
        Spliterator<String> source = lines.spliterator();
        lines.close();
        assertThrows(UncheckedIOException.class, () -> source.tryAdvance(line -> {
            throw new AssertionError("Closed reader returned a line");
        }));
    }

    @Test
    void directoryOnlyArchiveReturnsEmptyStream() throws IOException {
        try (Stream<String> lines = new SafepointLogFile(writeZip(false)).streamZipFile()) {
            assertEquals(0, lines.count());
        }
    }

    private Path writeZip(boolean includeFiles) throws IOException {
        Path archive = directory.resolve("safepoints.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            if (includeFiles) {
                zip.putNextEntry(new ZipEntry("logs/safepoints.log"));
                zip.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("logs/ignored.log"));
                zip.write("ignored\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }
}
