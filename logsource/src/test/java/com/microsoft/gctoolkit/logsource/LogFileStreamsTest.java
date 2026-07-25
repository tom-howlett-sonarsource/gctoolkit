// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LogFileStreamsTest {

    private static final List<String> LINES = List.of("first", "second", "third");
    private static final String PLAIN_TEXT_LOG = "gc.log";
    private static final String GZIP_LOG = "gc.log.gz";
    private static final String ZIP_LOG = "gc.log.zip";
    private static final String FIRST_ENTRY = "gc.log.0";
    private static final String SECOND_ENTRY = "gc.log.1";

    @TempDir
    Path directory;

    @Test
    public void plainTextLogIsStreamed() throws IOException {
        Path path = LogSourceTestFiles.plainText(directory, PLAIN_TEXT_LOG, LINES);
        assertEquals(LINES, collect(LogFileStreams.plainTextLines(path)));
    }

    @Test
    public void gzipLogIsStreamed() throws IOException {
        Path path = LogSourceTestFiles.gzip(directory, GZIP_LOG, LINES);
        assertEquals(LINES, collect(LogFileStreams.gzipLines(path)));
    }

    @Test
    public void firstZipEntryIsStreamedSkippingDirectoryEntries() throws IOException {
        Path path = LogSourceTestFiles.zip(directory, ZIP_LOG, List.of(FIRST_ENTRY, SECOND_ENTRY));
        assertEquals(List.of(LogSourceTestFiles.contentOf(FIRST_ENTRY)),
                collect(LogFileStreams.firstZipEntryLines(path)));
    }

    @Test
    public void namedZipEntryIsStreamed() throws IOException {
        Path path = LogSourceTestFiles.zip(directory, ZIP_LOG, List.of(FIRST_ENTRY, SECOND_ENTRY));
        assertEquals(List.of(LogSourceTestFiles.contentOf(SECOND_ENTRY)),
                collect(LogFileStreams.zipEntryLines(path, SECOND_ENTRY)));
    }

    @Test
    public void streamingAnUnknownZipEntryFails() throws IOException {
        Path path = LogSourceTestFiles.zip(directory, ZIP_LOG, List.of(FIRST_ENTRY));
        assertThrows(IOException.class, () -> LogFileStreams.zipEntryLines(path, "gc.log.9"));
    }

    @Test
    public void aNamedZipEntryStreamIsClosedWithoutHoldingTheArchive() throws IOException {
        Path path = LogSourceTestFiles.zip(directory, ZIP_LOG, List.of(FIRST_ENTRY));
        try (Stream<String> lines = LogFileStreams.zipEntryLines(path, FIRST_ENTRY)) {
            assertEquals(1, lines.count());
        }
        // Closing the stream closes the archive, so the file is no longer locked.
        Files.delete(path);
    }

    @Test
    public void streamingACorruptZipArchiveFails() throws IOException {
        Path path = Files.write(directory.resolve(ZIP_LOG), localFileHeaderWithTruncatedEntryName());
        assertThrows(IOException.class, () -> LogFileStreams.firstZipEntryLines(path));
    }

    @Test
    public void streamingALogThatIsNotGzipCompressedFails() throws IOException {
        Path path = LogSourceTestFiles.plainText(directory, PLAIN_TEXT_LOG, LINES);
        assertThrows(IOException.class, () -> LogFileStreams.gzipLines(path));
    }

    @Test
    public void linesAreOpenedAccordingToTheFormat() throws IOException {
        Path plainText = LogSourceTestFiles.plainText(directory, PLAIN_TEXT_LOG, LINES);
        Path gzip = LogSourceTestFiles.gzip(directory, GZIP_LOG, LINES);
        Path zip = LogSourceTestFiles.zip(directory, ZIP_LOG, List.of(PLAIN_TEXT_LOG));

        assertEquals(LINES, collect(LogFileStreams.lines(plainText, LogFileFormat.PLAINTEXT)));
        assertEquals(LINES, collect(LogFileStreams.lines(gzip, LogFileFormat.GZIP)));
        assertEquals(List.of(LogSourceTestFiles.contentOf(PLAIN_TEXT_LOG)),
                collect(LogFileStreams.lines(zip, LogFileFormat.ZIP)));
    }

    @Test
    public void linesCannotBeOpenedOnADirectory() throws IOException {
        Path path = Files.createDirectory(directory.resolve("rotating"));
        IOException ioe = assertThrows(IOException.class,
                () -> LogFileStreams.lines(path, LogFileFormat.DIRECTORY));
        assertEquals("Unable to read " + path, ioe.getMessage());
    }

    /**
     * A ZIP local file header that promises a 16 byte entry name the archive does not hold, so
     * reading the first entry runs off the end of the file.
     */
    private byte[] localFileHeaderWithTruncatedEntryName() {
        byte[] header = new byte[30];
        header[0] = 0x50;
        header[1] = 0x4b;
        header[2] = 0x03;
        header[3] = 0x04;
        header[26] = 0x10;
        return header;
    }

    private List<String> collect(Stream<String> lines) {
        try (Stream<String> stream = lines) {
            return stream.collect(Collectors.toList());
        }
    }
}
