// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Line streams that own their underlying input resources.
 */
public final class LogFileStreams {

    private LogFileStreams() {
    }

    /**
     * Read lines using the default charset, closing the reader and input when the stream is closed.
     * @param input the input whose ownership is transferred to the returned stream
     * @return a line stream which must be closed, including after a terminal operation
     */
    public static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return reader.lines().onClose(() -> close(reader));
    }

    /**
     * Read the first non-directory entry of a ZIP archive.
     * @param path the archive path
     * @return a line stream which owns the archive input and must be closed
     * @throws IOException if the archive cannot be opened
     */
    public static Stream<String> zipLines(Path path) throws IOException {
        return zipLines(new ZipInputStream(Files.newInputStream(path)));
    }

    static Stream<String> zipLines(ZipInputStream input) throws IOException {
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(new BufferedInputStream(input));
        } catch (IOException failure) {
            try {
                input.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    static void close(Closeable resource) {
        try {
            resource.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
