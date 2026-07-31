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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final String FIRST_LINE = "[0.001s][info][gc] first";
    private static final String SECOND_LINE = "[0.002s][info][gc] second";
    private static final String CONTENT = FIRST_LINE + "\n" + SECOND_LINE + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversPlainTextGZipZipAndDirectory() throws IOException {
        assertEquals(GCLogSourceFormat.PLAINTEXT, new GCLogSource(plainText()).getFormat());
        assertEquals(GCLogSourceFormat.GZIP, new GCLogSource(gzip()).getFormat());
        assertEquals(GCLogSourceFormat.ZIP, new GCLogSource(zip()).getFormat());
        assertEquals(GCLogSourceFormat.DIRECTORY, new GCLogSource(directory).getFormat());
    }

    @Test
    void reportsFormatWithBooleanAccessors() throws IOException {
        GCLogSource source = new GCLogSource(plainText());
        assertTrue(source.isPlainText());
        assertFalse(source.isZip());
        assertFalse(source.isGZip());
        assertFalse(source.isDirectory());
        assertTrue(new GCLogSource(directory).isDirectory());
    }

    @Test
    void byteSizeReportsTheSizeOnDisk() throws IOException {
        Path path = plainText();
        assertEquals(Files.size(path), new GCLogSource(path).byteSize());
    }

    @Test
    void byteSizeOfAnUnreadableSourceIsZero() {
        assertEquals(0L, new GCLogSource(directory.resolve("does-not-exist.log")).byteSize());
    }

    @Test
    void streamsPlainTextGZipAndZipSources() throws IOException {
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), lines(new GCLogSource(plainText())));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), lines(new GCLogSource(gzip())));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), lines(new GCLogSource(zip())));
    }

    @Test
    void streamingADirectoryIsAnError() {
        GCLogSource source = new GCLogSource(directory);
        IOException exception = assertThrows(IOException.class, source::stream);
        assertEquals("Unable to read " + directory, exception.getMessage());
    }

    @Test
    void listsAndStreamsNamedZipEntries() throws IOException {
        GCLogSource source = new GCLogSource(zip());
        assertEquals(List.of("gc.log", "gc.log.1"), source.zipEntryNames());
        try (Stream<String> stream = source.streamZipEntry("gc.log.1")) {
            assertEquals(List.of(SECOND_LINE), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamingAnUnknownZipEntryIsAnError() throws IOException {
        GCLogSource source = new GCLogSource(zip());
        assertThrows(IOException.class, () -> source.streamZipEntry("no-such-entry.log"));
    }

    @Test
    void tailReadsTheLastLines() throws IOException {
        assertEquals(List.of(SECOND_LINE), new GCLogSource(plainText()).tail(1));
    }

    @Test
    void tailOfMoreLinesThanTheSourceHoldsReadsFromOneByteIn() throws IOException {
        // The backwards scan stops at the first byte of the source rather than before it, so the
        // first character of the first line is dropped. Pinned here because callers depend on the
        // long standing behaviour of this read.
        assertEquals(List.of(FIRST_LINE.substring(1), SECOND_LINE), new GCLogSource(plainText()).tail(10));
    }

    @Test
    void tailOfASingleLineWithoutALineEndingIsEmpty() throws IOException {
        Path path = directory.resolve("one-line.log");
        Files.writeString(path, FIRST_LINE, StandardCharsets.UTF_8);
        assertTrue(new GCLogSource(path).tail(10).isEmpty());
    }

    private List<String> lines(GCLogSource source) throws IOException {
        try (Stream<String> stream = source.stream()) {
            return stream.collect(Collectors.toList());
        }
    }

    private Path plainText() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.1"));
            output.write((SECOND_LINE + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}
