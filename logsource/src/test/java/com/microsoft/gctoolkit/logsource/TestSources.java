// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates the log sources used by the tests in this module.
 */
final class TestSources {

    static final String LINE_ONE = "[0.001s][info][gc] first";
    static final String LINE_TWO = "[0.002s][info][gc] second";
    static final String CONTENT = LINE_ONE + "\n" + LINE_TWO + "\n";

    private TestSources() {
    }

    static Path plainText(Path directory, String name) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    static Path plainText(Path directory, String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    static Path gzip(Path directory, String name) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    static Path zip(Path directory, String name, String... entryNames) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String entryName : entryNames)
            entries.put(entryName, CONTENT);
        return zip(directory, name, entries);
    }

    static Path zip(Path directory, String name, Map<String, String> entries) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }
}
