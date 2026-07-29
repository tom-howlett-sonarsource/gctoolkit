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
 * Builds the log sources the tests in this package read.
 */
final class LogSources {

    private LogSources() {
    }

    static Path plainText(Path directory, String name, String... lines) throws IOException {
        Path path = directory.resolve(name);
        Files.write(path, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return path;
    }

    static Path gzip(Path directory, String name, String... lines) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    /**
     * Write a Zip file holding one directory entry followed by one file entry per given entry name.
     * Each file entry holds a single line naming the entry.
     */
    static Path zip(Path directory, String name, String... entryNames) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            for (String entryName : entryNames) {
                out.putNextEntry(new ZipEntry(entryName));
                out.write(("line in " + entryName).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return path;
    }
}
