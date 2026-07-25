// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the plain text, ZIP and GZIP log sources used by the tests in this package.
 */
final class LogSourceTestFiles {

    private LogSourceTestFiles() {}

    static Path plainText(Path directory, String name, List<String> lines) throws IOException {
        return Files.write(directory.resolve(name), lines);
    }

    static Path gzip(Path directory, String name, List<String> lines) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(join(lines));
        }
        return path;
    }

    /**
     * Write a ZIP archive holding a directory entry followed by one entry per supplied name. Each
     * entry contains a single line naming the entry, so that a stream can be traced back to it.
     */
    static Path zip(Path directory, String name, List<String> entryNames) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            for (String entryName : entryNames) {
                out.putNextEntry(new ZipEntry(entryName));
                out.write(join(List.of(contentOf(entryName))));
                out.closeEntry();
            }
        }
        return path;
    }

    static String contentOf(String entryName) {
        return "content of " + entryName;
    }

    private static byte[] join(List<String> lines) {
        return String.join(System.lineSeparator(), lines)
                .concat(System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
    }
}
