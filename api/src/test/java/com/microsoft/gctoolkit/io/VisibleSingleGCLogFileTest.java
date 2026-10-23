// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibleSingleGCLogFileTest {
    private static final String LOG_CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void streamsPlainAndGzipSources() throws IOException {
        assertContainsLogLine(writePlain());
        assertContainsLogLine(writeGzip());
    }

    @Test
    void preservesPublicApiAndMetadataBehavior() throws Exception {
        assertTrue(Modifier.isPublic(SingleGCLogFile.class.getModifiers()));
        assertTrue(Modifier.isPublic(SingleGCLogFile.class.getConstructor(Path.class).getModifiers()));
        assertEquals(LogFileMetadata.class, SingleGCLogFile.class.getDeclaredMethod("getMetaData").getReturnType());
        assertEquals(java.util.stream.Stream.class, SingleGCLogFile.class.getDeclaredMethod("stream").getReturnType());

        Path source = writePlain();
        SingleGCLogFile log = new SingleGCLogFile(source);
        assertEquals(source, log.getPath());
        assertTrue(log.getMetaData().isPlainText());
    }

    private void assertContainsLogLine(Path path) throws IOException {
        try (var lines = new SingleGCLogFile(path).stream()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertTrue(collected.contains("[0.001s][info][gc] test"));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}
