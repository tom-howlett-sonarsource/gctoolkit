// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibleRotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void exposesPublicLongMethod() throws Exception {
        Method method = RotatingLogFileMetadata.class.getDeclaredMethod("getTotalByteSize");
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(long.class, method.getReturnType());
        assertEquals(0, method.getParameterCount());
    }

    @Test
    void sumsDirectorySegmentsAndUncompressedZipEntries() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), first);
        Files.write(logs.resolve("gc.log"), current);
        assertEquals(first.length + current.length,
                new RotatingLogFileMetadata(logs).getTotalByteSize());

        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            addEntry(output, "logs/", new byte[0]);
            addEntry(output, "gc.log.0", first);
            addEntry(output, "gc.log", current);
        }
        assertEquals(first.length + current.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    private static void addEntry(ZipOutputStream output, String name, byte[] content)
            throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}
