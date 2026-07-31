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
final class LogSources {

    static final String FIRST_LINE = "[0.001s][info][gc] first";
    static final String LAST_LINE = "[0.002s][info][gc] last";
    static final String LOG_CONTENT = FIRST_LINE + "\n" + LAST_LINE + "\n";

    private LogSources() {
        // Utility class.
    }

    static Path writePlainText(Path directory, String fileName) throws IOException {
        Path path = directory.resolve(fileName);
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    static Path writeGzip(Path directory, String fileName) throws IOException {
        Path path = directory.resolve(fileName);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    /**
     * Write a Zip file. An entry name ending in a {@code /} is written as a directory entry.
     */
    static Path writeZip(Path directory, String fileName, String... entryNames) throws IOException {
        Path path = directory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entryName : entryNames) {
                output.putNextEntry(new ZipEntry(entryName));
                if (!entryName.endsWith("/"))
                    output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }
}
