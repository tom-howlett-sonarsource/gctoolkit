// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Whatever the log is packed in, the lines a caller sees are the trimmed, non-blank lines of the
 * log followed by the end of data sentinel.
 */
class SingleGCLogFileStreamTest {

    private static final String CONTENTS = "  [0.100s][info][gc] first  \n\n   \n[0.200s][info][gc] second\n";
    private static final List<String> EXPECTED = Arrays.asList(
            "[0.100s][info][gc] first", "[0.200s][info][gc] second", GCLogFile.END_OF_DATA_SENTINEL);

    @TempDir
    Path directory;

    @Test
    void plainTextLogStreamsItsLinesAndTheSentinel() throws IOException {
        Path log = directory.resolve("gc.log");
        Files.write(log, CONTENTS.getBytes(StandardCharsets.UTF_8));

        assertEquals(EXPECTED, collect(log));
    }

    @Test
    void zippedLogStreamsTheFirstEntryAndTheSentinel() throws IOException {
        Path log = directory.resolve("gc.log.zip");
        try (OutputStream bytes = Files.newOutputStream(log); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("directory/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("gc.log"));
            zip.write(CONTENTS.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertEquals(EXPECTED, collect(log));
    }

    @Test
    void gzippedLogStreamsItsLinesAndTheSentinel() throws IOException {
        Path log = directory.resolve("gc.log.gz");
        try (OutputStream bytes = Files.newOutputStream(log); OutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(CONTENTS.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(EXPECTED, collect(log));
    }

    @Test
    void aTruncatedGZipHeaderIsReportedRatherThanLeavingTheFileOpen() throws IOException {
        Path log = directory.resolve("truncated.log.gz");
        Files.write(log, new byte[]{0x1f, (byte) 0x8b, 0x08});
        SingleGCLogFile logFile = new SingleGCLogFile(log);

        assertThrows(IOException.class, logFile::stream);
    }

    @Test
    void anEmptyArchiveStreamsNothingButTheSentinel() throws IOException {
        Path log = directory.resolve("empty.zip");
        try (OutputStream bytes = Files.newOutputStream(log); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("directory/"));
            zip.closeEntry();
        }

        assertEquals(Arrays.asList(GCLogFile.END_OF_DATA_SENTINEL), collect(log));
    }

    private List<String> collect(Path log) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = new SingleGCLogFile(log).stream()) {
            stream.forEach(lines::add);
        }
        return lines;
    }
}
