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
 * Writes the log sources used by the tests in this module.
 */
final class LogSourceFixture {

    static final String LINE_ONE = "[0.001s][info][gc] first";
    static final String LINE_TWO = "[0.002s][info][gc] second";
    static final String CONTENT = LINE_ONE + "\n" + LINE_TWO + "\n";

    private LogSourceFixture() {
    }

    static Path writePlainText(Path directory, String fileName) throws IOException {
        Path path = directory.resolve(fileName);
        Files.write(path, CONTENT.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    static Path writeGZip(Path directory, String fileName) throws IOException {
        Path path = directory.resolve(fileName);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    /**
     * Write a ZIP source holding a directory entry, which readers are expected to skip,
     * followed by an entry for each of the given names.
     */
    static Path writeZip(Path directory, String fileName, String... entryNames) throws IOException {
        Path path = directory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            for (String entryName : entryNames) {
                output.putNextEntry(new ZipEntry(entryName));
                output.write((entryName + "\n" + CONTENT).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }
}
