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
 * Writes the log sources that the tests in this package read.
 */
final class LogSourceFixture {

    private LogSourceFixture() {
        // static utility
    }

    static Path writePlainText(Path directory, String name, String... lines) throws IOException {
        Path path = directory.resolve(name);
        Files.write(path, contentOf(lines).getBytes(StandardCharsets.UTF_8));
        return path;
    }

    static Path writeEmpty(Path directory, String name) throws IOException {
        Path path = directory.resolve(name);
        Files.write(path, new byte[0]);
        return path;
    }

    static Path writeGzip(Path directory, String name, String... lines) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(contentOf(lines).getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    static Path writeZip(Path directory, String name, String entryName, String... lines) throws IOException {
        return writeZip(directory, name, false, entryName, lines);
    }

    /**
     * Write a Zip archive holding a single log, optionally preceded by a directory entry so that
     * the tests can show that directory entries are skipped.
     */
    static Path writeZip(Path directory, String name, boolean withDirectoryEntry, String entryName, String... lines) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            if (withDirectoryEntry) {
                output.putNextEntry(new ZipEntry("logs/"));
                output.closeEntry();
            }
            output.putNextEntry(new ZipEntry(entryName));
            output.write(contentOf(lines).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private static String contentOf(String... lines) {
        return (lines.length == 0) ? "" : String.join("\n", lines) + "\n";
    }
}
