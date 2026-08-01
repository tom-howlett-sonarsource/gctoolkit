// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the small log sources that the tests in this module read back.
 */
final class LogSourceFixture {

    static final String FIRST_LINE = "[0.001s][info][gc] first";
    static final String SECOND_LINE = "[0.002s][info][gc] second";
    static final String CONTENT = FIRST_LINE + "\n" + SECOND_LINE + "\n";

    private LogSourceFixture() {
    }

    static Path empty(Path directory, String name) throws IOException {
        return Files.write(directory.resolve(name), new byte[0]);
    }

    static Path plainText(Path directory, String name) throws IOException {
        return plainText(directory, name, CONTENT);
    }

    static Path plainText(Path directory, String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
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
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            for (String entryName : entryNames) {
                output.putNextEntry(new ZipEntry(entryName));
                output.write(contentOf(entryName).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    static String contentOf(String entryName) {
        return entryName + " " + FIRST_LINE + "\n" + entryName + " " + SECOND_LINE + "\n";
    }
}
