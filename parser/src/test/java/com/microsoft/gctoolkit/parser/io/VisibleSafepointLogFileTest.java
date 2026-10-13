// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

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

class VisibleSafepointLogFileTest {
    private static final String LOG_CONTENT = "0.001: safepoint test\n";

    @TempDir
    Path directory;

    @Test
    void streamsPlainAndGzipSources() throws IOException {
        assertContainsLogLine(writePlain());
        assertContainsLogLine(writeGzip());
    }

    @Test
    void preservesPublicApiAndPathBehavior() throws Exception {
        assertTrue(Modifier.isPublic(SafepointLogFile.class.getModifiers()));
        assertTrue(Modifier.isPublic(SafepointLogFile.class.getConstructor(Path.class).getModifiers()));
        assertEquals(Path.class, SafepointLogFile.class.getDeclaredMethod("getPath").getReturnType());
        assertEquals(String.class, SafepointLogFile.class.getDeclaredMethod("endOfData").getReturnType());
        assertEquals(com.microsoft.gctoolkit.jvm.Diary.class, SafepointLogFile.class.getDeclaredMethod("diary").getReturnType());
        assertEquals(java.util.stream.Stream.class, SafepointLogFile.class.getDeclaredMethod("stream").getReturnType());

        Path source = writePlain();
        assertEquals(source, new SafepointLogFile(source).getPath());
    }

    private void assertContainsLogLine(Path path) throws IOException {
        try (var lines = new SafepointLogFile(path).stream()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertTrue(collected.contains("0.001: safepoint test"));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("safepoint.log");
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("safepoint.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}
